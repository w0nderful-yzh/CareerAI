from dataclasses import dataclass

from langchain_core.tools import BaseTool, StructuredTool
from pydantic import BaseModel

from careerai_agent.tools.client import BusinessToolClient, ToolCallContext
from careerai_agent.tools.models import (
    ApplyInterviewTurnInput,
    CreateImprovementPlanInput,
    CreateInterviewSessionInput,
    EmptyInput,
    InterviewBlueprint,
    InterviewCategory,
    InterviewPlanningContext,
    InterviewSession,
    InterviewSessionInput,
    InterviewTurnContext,
    InterviewTurnEvaluation,
    InterviewTurnResult,
    JobMatchReport,
    JobMatchTask,
    JobSnapshot,
    NextQuestionIntent,
    ResourceIdInput,
    ResumeDetail,
    ResumeImprovementPlan,
    ResumeSummary,
    StartJobMatchInput,
)


@dataclass(frozen=True)
class BusinessTools:
    list_resumes: BaseTool
    get_resume_detail: BaseTool
    get_job: BaseTool
    start_job_match: BaseTool
    get_job_match_task: BaseTool
    get_match_report: BaseTool
    create_improvement_plan: BaseTool
    get_improvement_plan: BaseTool
    create_interview_session: BaseTool
    get_interview_turn_context: BaseTool
    get_interview_planning_context: BaseTool
    apply_interview_turn: BaseTool


def build_business_tools(
    client: BusinessToolClient,
    context: ToolCallContext,
) -> BusinessTools:
    async def list_resumes() -> list[ResumeSummary]:
        return await client.list_resumes(context)

    async def get_resume_detail(resource_id: int) -> ResumeDetail:
        return await client.get_resume_detail(resource_id, context)

    async def get_job(resource_id: int) -> JobSnapshot:
        return await client.get_job(resource_id, context)

    async def start_job_match(resume_id: int, job_id: int) -> JobMatchTask:
        return await client.start_job_match(
            resume_id,
            job_id,
            context,
            idempotency_key=f"{context.run_id}:start_job_match",
        )

    async def get_job_match_task(resource_id: int) -> JobMatchTask:
        return await client.get_job_match_task(resource_id, context)

    async def get_match_report(resource_id: int) -> JobMatchReport:
        return await client.get_match_report(resource_id, context)

    async def create_improvement_plan(
        match_report_id: int,
        strategy: str,
        rationale: str,
        prioritized_gaps: list[str],
        supporting_evidence: list[str],
        interview_focus: list[str],
    ) -> ResumeImprovementPlan:
        return await client.create_improvement_plan(
            match_report_id,
            strategy,
            rationale,
            prioritized_gaps,
            supporting_evidence,
            interview_focus,
            context,
            idempotency_key=f"{context.run_id}:create_improvement_plan",
        )

    async def get_improvement_plan(resource_id: int) -> ResumeImprovementPlan:
        return await client.get_improvement_plan(resource_id, context)

    async def get_interview_turn_context(session_id: str) -> InterviewTurnContext:
        return await client.get_interview_turn_context(session_id, context)

    async def get_interview_planning_context() -> InterviewPlanningContext:
        return await client.get_interview_planning_context(context)

    async def create_interview_session(
        resume_text: str | None,
        question_count: int,
        resume_id: int | None,
        force_create: bool,
        llm_provider: str | None,
        skill_id: str,
        difficulty: str,
        custom_categories: list[InterviewCategory],
        jd_text: str | None,
        job_id: int | None,
        match_report_id: int | None,
        blueprint: InterviewBlueprint,
    ) -> InterviewSession:
        return await client.create_interview_session(
            resume_text=resume_text,
            question_count=question_count,
            resume_id=resume_id,
            force_create=force_create,
            llm_provider=llm_provider,
            skill_id=skill_id,
            difficulty=difficulty,
            custom_categories=custom_categories,
            jd_text=jd_text,
            job_id=job_id,
            match_report_id=match_report_id,
            blueprint=blueprint,
            context=context,
            idempotency_key=f"{context.run_id}:create_interview_session",
        )

    async def apply_interview_turn(
        session_id: str,
        question_index: int,
        answer: str,
        action: str,
        rationale: str,
        answer_score: int,
        feedback: str,
        difficulty_adjustment: str,
        next_question_intent: NextQuestionIntent | None = None,
        evaluation: InterviewTurnEvaluation | None = None,
        end_reason: str | None = None,
    ) -> InterviewTurnResult:
        if evaluation is None:
            raise ValueError("evaluation is required")
        return await client.apply_interview_turn(
            session_id,
            question_index,
            answer,
            action,
            rationale,
            answer_score,
            feedback,
            difficulty_adjustment,
            next_question_intent,
            evaluation,
            end_reason,
            "ANSWER",
            context,
            idempotency_key=f"{context.run_id}:question:{question_index}",
        )

    # StructuredTool 保留清晰 JSON Schema，后续可直接交给模型选择。
    return BusinessTools(
        list_resumes=StructuredTool.from_function(
            coroutine=list_resumes,
            name="list_resumes",
            description="列出当前用户可用于岗位匹配的简历。",
            args_schema=EmptyInput,
        ),
        get_resume_detail=StructuredTool.from_function(
            coroutine=get_resume_detail,
            name="get_resume_detail",
            description="读取当前用户指定简历的文本和最近分析摘要。",
            args_schema=ResourceIdInput,
        ),
        get_job=StructuredTool.from_function(
            coroutine=get_job,
            name="get_job",
            description="读取当前用户指定岗位的 JD 快照。",
            args_schema=ResourceIdInput,
        ),
        start_job_match=StructuredTool.from_function(
            coroutine=start_job_match,
            name="start_job_match",
            description="幂等创建简历与岗位的异步匹配任务。",
            args_schema=StartJobMatchInput,
        ),
        get_job_match_task=StructuredTool.from_function(
            coroutine=get_job_match_task,
            name="get_job_match_task",
            description="查询岗位匹配任务状态和已生成报告。",
            args_schema=ResourceIdInput,
        ),
        get_match_report=StructuredTool.from_function(
            coroutine=get_match_report,
            name="get_match_report",
            description="读取完成后的岗位匹配报告。",
            args_schema=ResourceIdInput,
        ),
        create_improvement_plan=StructuredTool.from_function(
            coroutine=create_improvement_plan,
            name="create_resume_improvement_plan",
            description="根据匹配报告和 Agent 选定的准备策略，幂等创建简历改进计划。",
            args_schema=CreateImprovementPlanInput,
        ),
        get_improvement_plan=StructuredTool.from_function(
            coroutine=get_improvement_plan,
            name="get_resume_improvement_plan",
            description="读取已创建的简历改进计划。",
            args_schema=ResourceIdInput,
        ),
        create_interview_session=StructuredTool.from_function(
            coroutine=create_interview_session,
            name="create_interview_session",
            description="执行结构化面试蓝图，幂等创建会话并生成首题。",
            args_schema=CreateInterviewSessionInput,
        ),
        get_interview_turn_context=StructuredTool.from_function(
            coroutine=get_interview_turn_context,
            name="get_interview_turn_context",
            description="读取当前面试题、历史问答、出题蓝图和 JD 证据矩阵。",
            args_schema=InterviewSessionInput,
        ),
        get_interview_planning_context=StructuredTool.from_function(
            coroutine=get_interview_planning_context,
            name="get_interview_planning_context",
            description="读取当前用户跨场次能力画像、未完成面试任务和最近待验证目标。",
            args_schema=EmptyInput,
        ),
        apply_interview_turn=StructuredTool.from_function(
            coroutine=apply_interview_turn,
            name="apply_interview_turn",
            description="提交回答和下一题意图，由 Java 校验、生成并持久化最终问题。",
            args_schema=ApplyInterviewTurnInput,
        ),
    )


def tool_result[T: BaseModel](value: object, result_type: type[T]) -> T:
    return value if isinstance(value, result_type) else result_type.model_validate(value)
