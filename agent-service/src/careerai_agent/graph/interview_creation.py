from dataclasses import dataclass
from typing import TypedDict, cast
from uuid import uuid4

from langgraph.graph import END, START, StateGraph
from langgraph.graph.state import CompiledStateGraph
from langgraph.runtime import Runtime

from careerai_agent.domain.models import CreateInterviewSessionRequest
from careerai_agent.services.blueprint import InterviewBlueprintPlanner
from careerai_agent.tools.business import build_business_tools, tool_result
from careerai_agent.tools.client import BusinessToolClient, ToolCallContext
from careerai_agent.tools.models import (
    InterviewBlueprint,
    InterviewCategory,
    InterviewPlanningContext,
    InterviewSession,
    JobMatchReport,
    JobSnapshot,
    ResumeDetail,
)


@dataclass(frozen=True)
class InterviewCreationRuntimeContext:
    authorization: str
    run_id: str


class InterviewCreationState(TypedDict):
    request: CreateInterviewSessionRequest
    resume: ResumeDetail | None
    job: JobSnapshot | None
    report: JobMatchReport | None
    planning_context: InterviewPlanningContext | None
    blueprint: InterviewBlueprint | None
    session: InterviewSession | None


def build_interview_creation_graph(
    planner: InterviewBlueprintPlanner,
    business_client: BusinessToolClient,
) -> CompiledStateGraph[
    InterviewCreationState,
    InterviewCreationRuntimeContext,
    InterviewCreationState,
    InterviewCreationState,
]:
    """创建面试的三段式流程：读取事实、规划蓝图、调用 Java 创建真实 Session。"""

    async def load_context(
        state: InterviewCreationState,
        runtime: Runtime[InterviewCreationRuntimeContext],
    ) -> InterviewCreationState:
        # 所有简历、岗位和历史画像都从 Java Tool 读取，Python 不直接访问业务库。
        request = state["request"]
        tools = build_business_tools(business_client, _context(runtime, "load_blueprint_context"))
        resume = None
        job = None
        report = None
        planning_value = await tools.get_interview_planning_context.ainvoke({})
        planning_context = tool_result(planning_value, InterviewPlanningContext)
        if request.resume_id is not None:
            value = await tools.get_resume_detail.ainvoke({"resource_id": request.resume_id})
            resume = tool_result(value, ResumeDetail)
        if request.job_id is not None:
            value = await tools.get_job.ainvoke({"resource_id": request.job_id})
            job = tool_result(value, JobSnapshot)
        if request.match_report_id is not None:
            value = await tools.get_match_report.ainvoke({"resource_id": request.match_report_id})
            report = tool_result(value, JobMatchReport)
        return {
            **state,
            "resume": resume,
            "job": job,
            "report": report,
            "planning_context": planning_context,
        }

    async def plan_blueprint(state: InterviewCreationState) -> InterviewCreationState:
        planning_context = state["planning_context"]
        if planning_context is None:
            raise RuntimeError("interview planning context is missing")
        blueprint = await planner.plan(
            state["request"],
            state["resume"],
            state["job"],
            state["report"],
            planning_context,
        )
        return {**state, "blueprint": blueprint}

    async def create_session(
        state: InterviewCreationState,
        runtime: Runtime[InterviewCreationRuntimeContext],
    ) -> InterviewCreationState:
        # 蓝图只是受控命令；幂等、开场题生成和持久化仍由 Java 完成。
        request = state["request"]
        blueprint = state["blueprint"]
        if blueprint is None:
            raise RuntimeError("interview blueprint is missing")
        tools = build_business_tools(business_client, _context(runtime, "create_interview_session"))
        value = await tools.create_interview_session.ainvoke(
            {
                "resume_text": request.resume_text,
                "question_count": request.question_count,
                "resume_id": request.resume_id,
                "force_create": request.force_create,
                "llm_provider": request.llm_provider,
                "skill_id": request.skill_id,
                "difficulty": request.difficulty,
                "custom_categories": [
                    InterviewCategory.model_validate(item) for item in request.custom_categories
                ],
                "jd_text": request.jd_text,
                "job_id": request.job_id,
                "match_report_id": request.match_report_id,
                "blueprint": blueprint,
            }
        )
        return {**state, "session": tool_result(value, InterviewSession)}

    builder = StateGraph(InterviewCreationState, context_schema=InterviewCreationRuntimeContext)
    builder.add_node("load_context", load_context)
    builder.add_node("plan_blueprint", plan_blueprint)
    builder.add_node("create_session", create_session)
    builder.add_edge(START, "load_context")
    builder.add_edge("load_context", "plan_blueprint")
    builder.add_edge("plan_blueprint", "create_session")
    builder.add_edge("create_session", END)
    return builder.compile()


class InterviewCreationService:
    def __init__(
        self,
        graph: CompiledStateGraph[
            InterviewCreationState,
            InterviewCreationRuntimeContext,
            InterviewCreationState,
            InterviewCreationState,
        ],
    ) -> None:
        self._graph = graph

    async def create_session(
        self,
        request: CreateInterviewSessionRequest,
        authorization: str,
        user_id: str,
    ) -> InterviewSession:
        initial: InterviewCreationState = {
            "request": request,
            "resume": None,
            "job": None,
            "report": None,
            "planning_context": None,
            "blueprint": None,
            "session": None,
        }
        state = await self._graph.ainvoke(
            initial,
            context=InterviewCreationRuntimeContext(
                authorization=authorization,
                run_id=f"interview-create:{user_id}:{uuid4().hex}",
            ),
        )
        session = cast(InterviewCreationState, state)["session"]
        if session is None:
            raise RuntimeError("interview creation graph completed without a session")
        return session


def _context(
    runtime: Runtime[InterviewCreationRuntimeContext],
    step_id: str,
) -> ToolCallContext:
    return ToolCallContext(
        authorization=runtime.context.authorization,
        run_id=runtime.context.run_id,
        step_id=step_id,
    )
