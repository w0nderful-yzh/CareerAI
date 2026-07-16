from typing import Any

import httpx
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.graph import END, START, StateGraph
from langgraph.graph.state import CompiledStateGraph
from langgraph.runtime import Runtime
from pydantic import ValidationError

from careerai_agent.domain.models import RunStatus, StepStatus
from careerai_agent.graph.state import CareerAgentState, GraphRuntimeContext
from careerai_agent.services.decision import (
    DecisionMakingError,
    PreparationAction,
    PreparationDecision,
    PreparationDecisionMaker,
)
from careerai_agent.services.planner import AgentPlanner
from careerai_agent.tools.business import build_business_tools, tool_result
from careerai_agent.tools.client import BusinessToolClient, BusinessToolError, ToolCallContext
from careerai_agent.tools.models import (
    JobMatchReport,
    JobMatchTask,
    JobSnapshot,
    ResumeDetail,
    ResumeImprovementPlan,
    ResumeSummary,
)


def build_graph(
    checkpointer: BaseCheckpointSaver[str],
    planner: AgentPlanner,
    decision_maker: PreparationDecisionMaker,
    business_client: BusinessToolClient,
) -> CompiledStateGraph[
    CareerAgentState,
    GraphRuntimeContext,
    CareerAgentState,
    CareerAgentState,
]:
    async def dispatch(state: CareerAgentState) -> CareerAgentState:
        return state

    async def initialize_plan(state: CareerAgentState) -> CareerAgentState:
        steps = await planner.create_plan(state["goal"], state["constraints"])
        return {
            **state,
            "status": RunStatus.RUNNING.value,
            "plan": [
                {"id": step.id, "title": step.title, "status": StepStatus.PENDING.value}
                for step in steps
            ],
            "pause_reason": None,
        }

    async def load_context(
        state: CareerAgentState,
        runtime: Runtime[GraphRuntimeContext],
    ) -> CareerAgentState:
        job_id = _positive_int(state["constraints"].get("jobId"))
        if job_id is None:
            return {
                **state,
                "status": RunStatus.PAUSED.value,
                "pause_reason": "MISSING_JOB_ID",
            }

        context = _tool_context(state, runtime, "load_business_context")
        tools = build_business_tools(business_client, context)
        try:
            resume_id = _positive_int(state["constraints"].get("resumeId"))
            selection_reason = "USER_SELECTED"
            if resume_id is None:
                raw_resumes = await tools.list_resumes.ainvoke({})
                resumes = _resume_list(raw_resumes)
                if not resumes:
                    return {
                        **state,
                        "status": RunStatus.PAUSED.value,
                        "pause_reason": "NO_AVAILABLE_RESUME",
                    }
                # 首版按最近分析分选择，后续再让模型结合岗位证据比较多份简历。
                selected = max(resumes, key=lambda item: (item.latest_score or -1, item.id))
                resume_id = selected.id
                selection_reason = "HIGHEST_LATEST_SCORE"

            resume = tool_result(
                await tools.get_resume_detail.ainvoke({"resource_id": resume_id}),
                ResumeDetail,
            )
            job = tool_result(
                await tools.get_job.ainvoke({"resource_id": job_id}),
                JobSnapshot,
            )
        except (BusinessToolError, httpx.HTTPError, ValidationError) as exc:
            return _failed(state, "load_business_context", exc, plan_index=0)

        artifact = {
            "type": "business_context",
            "resumeId": resume.id,
            "resumeFilename": resume.filename,
            "resumeLatestScore": resume.latest_score,
            "jobId": job.id,
            "jobTitle": job.title,
            "selectionReason": selection_reason,
        }
        return {
            **state,
            "status": RunStatus.RUNNING.value,
            "selected_resume_id": resume.id,
            "job_id": job.id,
            "artifacts": _upsert_artifact(state["artifacts"], artifact),
            "plan": _set_plan_status(state["plan"], 0, StepStatus.COMPLETED),
            "pause_reason": None,
        }

    async def start_job_match(
        state: CareerAgentState,
        runtime: Runtime[GraphRuntimeContext],
    ) -> CareerAgentState:
        resume_id = state["selected_resume_id"]
        job_id = state["job_id"]
        if resume_id is None or job_id is None:
            return _failed_message(state, "start_job_match", "缺少简历或岗位 ID", 1)

        tools = build_business_tools(
            business_client,
            _tool_context(state, runtime, "start_job_match"),
        )
        try:
            task = tool_result(
                await tools.start_job_match.ainvoke({"resume_id": resume_id, "job_id": job_id}),
                JobMatchTask,
            )
        except (BusinessToolError, httpx.HTTPError, ValidationError) as exc:
            return _failed(state, "start_job_match", exc, plan_index=1)

        report_id = task.report_id or (task.report.id if task.report is not None else None)
        artifact = {
            "type": "job_match_task",
            "taskId": task.id,
            "status": task.status,
            "reportId": report_id,
        }
        return {
            **state,
            "status": RunStatus.RUNNING.value,
            "match_task_id": task.id,
            "match_report_id": report_id,
            "artifacts": _upsert_artifact(state["artifacts"], artifact),
            "plan": _set_plan_status(state["plan"], 1, StepStatus.COMPLETED),
        }

    async def poll_job_match(
        state: CareerAgentState,
        runtime: Runtime[GraphRuntimeContext],
    ) -> CareerAgentState:
        task_id = state["match_task_id"]
        if task_id is None:
            return _failed_message(state, "poll_job_match", "缺少匹配任务 ID", 2)

        tools = build_business_tools(
            business_client,
            _tool_context(state, runtime, "poll_job_match"),
        )
        try:
            task = tool_result(
                await tools.get_job_match_task.ainvoke({"resource_id": task_id}),
                JobMatchTask,
            )
            if task.status in {"PENDING", "PROCESSING"}:
                return {
                    **state,
                    "status": RunStatus.WAITING_ASYNC.value,
                    "poll_count": state["poll_count"] + 1,
                    "pause_reason": "JOB_MATCH_IN_PROGRESS",
                    "plan": _set_plan_status(state["plan"], 2, StepStatus.IN_PROGRESS),
                    "artifacts": _upsert_artifact(
                        state["artifacts"],
                        {
                            "type": "job_match_task",
                            "taskId": task.id,
                            "status": task.status,
                            "reportId": task.report_id,
                        },
                    ),
                }
            if task.status == "FAILED":
                return _failed_message(
                    state,
                    "poll_job_match",
                    task.error_message or "岗位匹配任务失败",
                    2,
                )

            report_id = task.report_id or (task.report.id if task.report is not None else None)
            if report_id is None:
                return _failed_message(state, "poll_job_match", "匹配任务完成但缺少报告", 2)
            report = task.report or tool_result(
                await tools.get_match_report.ainvoke({"resource_id": report_id}),
                JobMatchReport,
            )
        except (BusinessToolError, httpx.HTTPError, ValidationError) as exc:
            return _failed(state, "poll_job_match", exc, plan_index=2)

        return {
            **state,
            "status": RunStatus.RUNNING.value,
            "match_report_id": report.id,
            "poll_count": state["poll_count"] + 1,
            "pause_reason": None,
            "plan": _set_plan_status(state["plan"], 2, StepStatus.COMPLETED),
            "artifacts": _upsert_artifact(
                state["artifacts"],
                {"type": "job_match_report", **report.model_dump(mode="json", by_alias=True)},
            ),
        }

    async def create_improvement_plan(
        state: CareerAgentState,
        runtime: Runtime[GraphRuntimeContext],
    ) -> CareerAgentState:
        report_id = state["match_report_id"]
        if report_id is None:
            return _failed_message(state, "create_improvement_plan", "缺少匹配报告 ID", 4)

        try:
            decision = _get_preparation_decision(state["artifacts"])
        except ValidationError as exc:
            return _failed_message(
                state,
                "create_improvement_plan",
                f"缺少有效的 Agent 准备策略：{exc}",
                4,
            )
        if decision is None:
            return _failed_message(state, "create_improvement_plan", "缺少 Agent 准备策略", 4)

        tools = build_business_tools(
            business_client,
            _tool_context(state, runtime, "create_improvement_plan"),
        )
        try:
            plan = tool_result(
                await tools.create_improvement_plan.ainvoke(
                    {
                        "match_report_id": report_id,
                        "strategy": decision.strategy.value,
                        "rationale": decision.rationale,
                        "prioritized_gaps": decision.prioritized_gaps,
                        "supporting_evidence": decision.supporting_evidence,
                        "interview_focus": decision.interview_focus,
                    }
                ),
                ResumeImprovementPlan,
            )
        except (BusinessToolError, httpx.HTTPError, ValidationError) as exc:
            return _failed(state, "create_improvement_plan", exc, plan_index=4)

        return {
            **state,
            "status": RunStatus.COMPLETED.value,
            "improvement_plan_id": plan.id,
            "pause_reason": None,
            "plan": _complete_remaining(_set_plan_status(state["plan"], 4, StepStatus.COMPLETED)),
            "artifacts": _upsert_artifact(
                state["artifacts"],
                {
                    "type": "resume_improvement_plan",
                    **plan.model_dump(mode="json", by_alias=True),
                },
            ),
        }

    async def decide_preparation_strategy(state: CareerAgentState) -> CareerAgentState:
        report_id = state["match_report_id"]
        if report_id is None:
            return _failed_message(state, "decide_preparation_strategy", "缺少匹配报告 ID", 3)

        report_artifact = _find_artifact(state["artifacts"], "job_match_report")
        if report_artifact is None:
            return _failed_message(state, "decide_preparation_strategy", "缺少匹配报告证据", 3)

        try:
            report_payload = {key: value for key, value in report_artifact.items() if key != "type"}
            report = JobMatchReport.model_validate(report_payload)
            decision = await decision_maker.decide(
                state["goal"],
                state["constraints"],
                report,
            )
        except (DecisionMakingError, ValidationError) as exc:
            return _failed_message(state, "decide_preparation_strategy", str(exc), 3)

        artifact = {
            "type": "preparation_decision",
            **decision.model_dump(mode="json", by_alias=True),
            "selectedTool": (
                "create_resume_improvement_plan"
                if decision.action == PreparationAction.CREATE_IMPROVEMENT_PLAN
                else None
            ),
        }
        # 决策产物随 Checkpoint 持久化，前端据此解释 Agent 为什么调用或跳过写 Tool。
        if decision.action == PreparationAction.COMPLETE_WITH_MATCH_REPORT:
            plan = _set_plan_status(state["plan"], 3, StepStatus.COMPLETED)
            plan = _set_plan_status(plan, 4, StepStatus.SKIPPED)
            return {
                **state,
                "status": RunStatus.COMPLETED.value,
                "pause_reason": None,
                "plan": plan,
                "artifacts": _upsert_artifact(state["artifacts"], artifact),
            }
        return {
            **state,
            "status": RunStatus.RUNNING.value,
            "pause_reason": None,
            "plan": _set_plan_status(state["plan"], 3, StepStatus.COMPLETED),
            "artifacts": _upsert_artifact(state["artifacts"], artifact),
        }

    def route_from_dispatch(state: CareerAgentState) -> str:
        if state["status"] in {
            RunStatus.COMPLETED.value,
            RunStatus.FAILED.value,
            RunStatus.CANCELLED.value,
        }:
            return "end"
        if not state["plan"]:
            return "initialize_plan"
        if state["selected_resume_id"] is None or state["job_id"] is None:
            return "load_context"
        if state["match_task_id"] is None:
            return "start_job_match"
        if state["match_report_id"] is None:
            return "poll_job_match"
        decision = _get_preparation_decision(state["artifacts"])
        if decision is None:
            return "decide_preparation_strategy"
        if decision.action == PreparationAction.COMPLETE_WITH_MATCH_REPORT:
            return "end"
        if state["improvement_plan_id"] is None:
            return "create_improvement_plan"
        return "end"

    def route_after_context(state: CareerAgentState) -> str:
        return "start_job_match" if state["status"] == RunStatus.RUNNING.value else "end"

    def route_after_start(state: CareerAgentState) -> str:
        return "poll_job_match" if state["status"] == RunStatus.RUNNING.value else "end"

    def route_after_poll(state: CareerAgentState) -> str:
        return (
            "decide_preparation_strategy" if state["status"] == RunStatus.RUNNING.value else "end"
        )

    def route_after_decision(state: CareerAgentState) -> str:
        return "create_improvement_plan" if state["status"] == RunStatus.RUNNING.value else "end"

    builder = StateGraph(CareerAgentState, context_schema=GraphRuntimeContext)
    builder.add_node("dispatch", dispatch)
    builder.add_node("initialize_plan", initialize_plan)
    builder.add_node("load_context", load_context)
    builder.add_node("start_job_match", start_job_match)
    builder.add_node("poll_job_match", poll_job_match)
    builder.add_node("decide_preparation_strategy", decide_preparation_strategy)
    builder.add_node("create_improvement_plan", create_improvement_plan)
    builder.add_edge(START, "dispatch")
    builder.add_conditional_edges(
        "dispatch",
        route_from_dispatch,
        {
            "initialize_plan": "initialize_plan",
            "load_context": "load_context",
            "start_job_match": "start_job_match",
            "poll_job_match": "poll_job_match",
            "decide_preparation_strategy": "decide_preparation_strategy",
            "create_improvement_plan": "create_improvement_plan",
            "end": END,
        },
    )
    builder.add_edge("initialize_plan", "load_context")
    builder.add_conditional_edges(
        "load_context",
        route_after_context,
        {"start_job_match": "start_job_match", "end": END},
    )
    builder.add_conditional_edges(
        "start_job_match",
        route_after_start,
        {"poll_job_match": "poll_job_match", "end": END},
    )
    builder.add_conditional_edges(
        "poll_job_match",
        route_after_poll,
        {"decide_preparation_strategy": "decide_preparation_strategy", "end": END},
    )
    builder.add_conditional_edges(
        "decide_preparation_strategy",
        route_after_decision,
        {"create_improvement_plan": "create_improvement_plan", "end": END},
    )
    builder.add_edge("create_improvement_plan", END)
    return builder.compile(checkpointer=checkpointer)


def _tool_context(
    state: CareerAgentState,
    runtime: Runtime[GraphRuntimeContext],
    step_id: str,
) -> ToolCallContext:
    return ToolCallContext(
        authorization=runtime.context.authorization,
        run_id=state["id"],
        step_id=step_id,
    )


def _positive_int(value: object) -> int | None:
    return value if isinstance(value, int) and not isinstance(value, bool) and value > 0 else None


def _resume_list(value: object) -> list[ResumeSummary]:
    if not isinstance(value, list):
        raise BusinessToolError(502, "list_resumes 返回值不是列表")
    return [tool_result(item, ResumeSummary) for item in value]


def _set_plan_status(
    plan: list[dict[str, Any]],
    index: int,
    status: StepStatus,
) -> list[dict[str, Any]]:
    updated = [dict(step) for step in plan]
    if 0 <= index < len(updated):
        updated[index]["status"] = status.value
    return updated


def _complete_remaining(plan: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        {**step, "status": StepStatus.COMPLETED.value}
        if step.get("status") in {StepStatus.PENDING.value, StepStatus.IN_PROGRESS.value}
        else step
        for step in plan
    ]


def _upsert_artifact(
    artifacts: list[dict[str, Any]],
    artifact: dict[str, Any],
) -> list[dict[str, Any]]:
    artifact_type = artifact.get("type")
    return [item for item in artifacts if item.get("type") != artifact_type] + [artifact]


def _find_artifact(
    artifacts: list[dict[str, Any]],
    artifact_type: str,
) -> dict[str, Any] | None:
    return next((item for item in artifacts if item.get("type") == artifact_type), None)


def _get_preparation_decision(
    artifacts: list[dict[str, Any]],
) -> PreparationDecision | None:
    artifact = _find_artifact(artifacts, "preparation_decision")
    if artifact is None:
        return None
    return PreparationDecision.model_validate(
        {key: value for key, value in artifact.items() if key not in {"type", "selectedTool"}}
    )


def _failed(
    state: CareerAgentState,
    step_id: str,
    exc: BusinessToolError | httpx.HTTPError | ValidationError,
    plan_index: int,
) -> CareerAgentState:
    if isinstance(exc, BusinessToolError):
        code = exc.code
        message = exc.message
        retryable = exc.retryable
    elif isinstance(exc, httpx.HTTPError):
        code = 503
        message = str(exc)
        retryable = True
    else:
        code = 502
        message = str(exc)
        retryable = False
    return {
        **state,
        "status": RunStatus.FAILED.value,
        "pause_reason": None,
        "errors": [
            *state["errors"],
            {"stepId": step_id, "code": code, "message": message, "retryable": retryable},
        ],
        "plan": _set_plan_status(state["plan"], plan_index, StepStatus.FAILED),
    }


def _failed_message(
    state: CareerAgentState,
    step_id: str,
    message: str,
    plan_index: int,
) -> CareerAgentState:
    return {
        **state,
        "status": RunStatus.FAILED.value,
        "pause_reason": None,
        "errors": [
            *state["errors"],
            {"stepId": step_id, "code": 500, "message": message, "retryable": False},
        ],
        "plan": _set_plan_status(state["plan"], plan_index, StepStatus.FAILED),
    }
