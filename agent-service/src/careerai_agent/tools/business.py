from dataclasses import dataclass

from langchain_core.tools import BaseTool, StructuredTool
from pydantic import BaseModel

from careerai_agent.tools.client import BusinessToolClient, ToolCallContext
from careerai_agent.tools.models import (
    CreateImprovementPlanInput,
    EmptyInput,
    JobMatchReport,
    JobMatchTask,
    JobSnapshot,
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

    async def create_improvement_plan(match_report_id: int) -> ResumeImprovementPlan:
        return await client.create_improvement_plan(
            match_report_id,
            context,
            idempotency_key=f"{context.run_id}:create_improvement_plan",
        )

    async def get_improvement_plan(resource_id: int) -> ResumeImprovementPlan:
        return await client.get_improvement_plan(resource_id, context)

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
            description="根据匹配报告幂等创建简历改进计划。",
            args_schema=CreateImprovementPlanInput,
        ),
        get_improvement_plan=StructuredTool.from_function(
            coroutine=get_improvement_plan,
            name="get_resume_improvement_plan",
            description="读取已创建的简历改进计划。",
            args_schema=ResourceIdInput,
        ),
    )


def tool_result[T: BaseModel](value: object, result_type: type[T]) -> T:
    return value if isinstance(value, result_type) else result_type.model_validate(value)
