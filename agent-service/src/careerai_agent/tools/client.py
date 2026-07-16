from collections.abc import Mapping
from dataclasses import dataclass
from typing import Any, Protocol

import httpx

from careerai_agent.tools.models import (
    InterviewBlueprint,
    InterviewCategory,
    InterviewPlanningContext,
    InterviewSession,
    InterviewTurnContext,
    InterviewTurnEvaluation,
    InterviewTurnResult,
    JobMatchReport,
    JobMatchTask,
    JobSnapshot,
    NextQuestionIntent,
    ResumeDetail,
    ResumeImprovementPlan,
    ResumeSummary,
)


class BusinessToolError(RuntimeError):
    def __init__(self, code: int, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.retryable = 500 <= code < 600 or code in {7001, 7002, 7003}


@dataclass(frozen=True)
class ToolCallContext:
    authorization: str
    run_id: str
    step_id: str


class BusinessToolClient(Protocol):
    async def list_resumes(self, context: ToolCallContext) -> list[ResumeSummary]: ...

    async def get_resume_detail(
        self,
        resume_id: int,
        context: ToolCallContext,
    ) -> ResumeDetail: ...

    async def get_job(self, job_id: int, context: ToolCallContext) -> JobSnapshot: ...

    async def start_job_match(
        self,
        resume_id: int,
        job_id: int,
        context: ToolCallContext,
        idempotency_key: str,
    ) -> JobMatchTask: ...

    async def get_job_match_task(
        self,
        task_id: int,
        context: ToolCallContext,
    ) -> JobMatchTask: ...

    async def get_match_report(
        self,
        report_id: int,
        context: ToolCallContext,
    ) -> JobMatchReport: ...

    async def create_improvement_plan(
        self,
        match_report_id: int,
        strategy: str,
        rationale: str,
        prioritized_gaps: list[str],
        supporting_evidence: list[str],
        interview_focus: list[str],
        context: ToolCallContext,
        idempotency_key: str,
    ) -> ResumeImprovementPlan: ...

    async def get_improvement_plan(
        self,
        plan_id: int,
        context: ToolCallContext,
    ) -> ResumeImprovementPlan: ...

    async def get_interview_turn_context(
        self,
        session_id: str,
        context: ToolCallContext,
    ) -> InterviewTurnContext: ...

    async def get_interview_planning_context(
        self,
        context: ToolCallContext,
    ) -> InterviewPlanningContext: ...

    async def create_interview_session(
        self,
        *,
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
        context: ToolCallContext,
        idempotency_key: str,
    ) -> InterviewSession: ...

    async def apply_interview_turn(
        self,
        session_id: str,
        question_index: int,
        answer: str | None,
        action: str,
        rationale: str,
        answer_score: int,
        feedback: str,
        difficulty_adjustment: str,
        next_question_intent: NextQuestionIntent | None,
        evaluation: InterviewTurnEvaluation | None,
        end_reason: str | None,
        intent: str,
        context: ToolCallContext,
        idempotency_key: str,
    ) -> InterviewTurnResult: ...


class AgentBusinessToolClient:
    def __init__(
        self,
        base_url: str,
        service_token: str | None,
        timeout_seconds: float,
    ) -> None:
        self._service_token = service_token
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/"),
            timeout=timeout_seconds,
        )

    async def aclose(self) -> None:
        await self._client.aclose()

    async def list_resumes(self, context: ToolCallContext) -> list[ResumeSummary]:
        data = await self._request("GET", "/internal/agent/tools/resumes", context)
        if not isinstance(data, list):
            raise BusinessToolError(502, "Java Agent 返回了无效的简历列表")
        return [ResumeSummary.model_validate(item) for item in data]

    async def get_resume_detail(
        self,
        resume_id: int,
        context: ToolCallContext,
    ) -> ResumeDetail:
        data = await self._request(
            "GET",
            f"/internal/agent/tools/resumes/{resume_id}",
            context,
        )
        return ResumeDetail.model_validate(data)

    async def get_job(self, job_id: int, context: ToolCallContext) -> JobSnapshot:
        data = await self._request("GET", f"/internal/agent/tools/jobs/{job_id}", context)
        return JobSnapshot.model_validate(data)

    async def start_job_match(
        self,
        resume_id: int,
        job_id: int,
        context: ToolCallContext,
        idempotency_key: str,
    ) -> JobMatchTask:
        data = await self._request(
            "POST",
            "/internal/agent/tools/job-match-tasks",
            context,
            json={"resumeId": resume_id, "jobId": job_id},
            idempotency_key=idempotency_key,
        )
        return JobMatchTask.model_validate(data)

    async def get_job_match_task(
        self,
        task_id: int,
        context: ToolCallContext,
    ) -> JobMatchTask:
        data = await self._request(
            "GET",
            f"/internal/agent/tools/job-match-tasks/{task_id}",
            context,
        )
        return JobMatchTask.model_validate(data)

    async def get_match_report(
        self,
        report_id: int,
        context: ToolCallContext,
    ) -> JobMatchReport:
        data = await self._request(
            "GET",
            f"/internal/agent/tools/job-match-reports/{report_id}",
            context,
        )
        return JobMatchReport.model_validate(data)

    async def create_improvement_plan(
        self,
        match_report_id: int,
        strategy: str,
        rationale: str,
        prioritized_gaps: list[str],
        supporting_evidence: list[str],
        interview_focus: list[str],
        context: ToolCallContext,
        idempotency_key: str,
    ) -> ResumeImprovementPlan:
        data = await self._request(
            "POST",
            "/internal/agent/tools/resume-improvement-plans",
            context,
            json={
                "matchReportId": match_report_id,
                "strategy": strategy,
                "rationale": rationale,
                "prioritizedGaps": prioritized_gaps,
                "supportingEvidence": supporting_evidence,
                "interviewFocus": interview_focus,
            },
            idempotency_key=idempotency_key,
        )
        return ResumeImprovementPlan.model_validate(data)

    async def get_improvement_plan(
        self,
        plan_id: int,
        context: ToolCallContext,
    ) -> ResumeImprovementPlan:
        data = await self._request(
            "GET",
            f"/internal/agent/tools/resume-improvement-plans/{plan_id}",
            context,
        )
        return ResumeImprovementPlan.model_validate(data)

    async def get_interview_turn_context(
        self,
        session_id: str,
        context: ToolCallContext,
    ) -> InterviewTurnContext:
        data = await self._request(
            "GET",
            f"/internal/agent/tools/interview-sessions/{session_id}/turn-context",
            context,
        )
        return InterviewTurnContext.model_validate(data)

    async def get_interview_planning_context(
        self,
        context: ToolCallContext,
    ) -> InterviewPlanningContext:
        data = await self._request(
            "GET",
            "/internal/agent/tools/interview-planning-context",
            context,
        )
        return InterviewPlanningContext.model_validate(data)

    async def create_interview_session(
        self,
        *,
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
        context: ToolCallContext,
        idempotency_key: str,
    ) -> InterviewSession:
        data = await self._request(
            "POST",
            "/internal/agent/tools/interview-sessions",
            context,
            json={
                "resumeText": resume_text,
                "questionCount": question_count,
                "resumeId": resume_id,
                "forceCreate": force_create,
                "llmProvider": llm_provider,
                "skillId": skill_id,
                "difficulty": difficulty,
                "customCategories": [
                    category.model_dump(by_alias=True) for category in custom_categories
                ],
                "jdText": jd_text,
                "jobId": job_id,
                "matchReportId": match_report_id,
                "blueprint": blueprint.model_dump(by_alias=True),
            },
            idempotency_key=idempotency_key,
        )
        return InterviewSession.model_validate(data)

    async def apply_interview_turn(
        self,
        session_id: str,
        question_index: int,
        answer: str | None,
        action: str,
        rationale: str,
        answer_score: int,
        feedback: str,
        difficulty_adjustment: str,
        next_question_intent: NextQuestionIntent | None,
        evaluation: InterviewTurnEvaluation | None,
        end_reason: str | None,
        intent: str,
        context: ToolCallContext,
        idempotency_key: str,
    ) -> InterviewTurnResult:
        data = await self._request(
            "POST",
            f"/internal/agent/tools/interview-sessions/{session_id}/turns",
            context,
            json={
                "questionIndex": question_index,
                "answer": answer,
                "action": action,
                "rationale": rationale,
                "answerScore": answer_score,
                "feedback": feedback,
                "difficultyAdjustment": difficulty_adjustment,
                "nextQuestionIntent": (
                    next_question_intent.model_dump(by_alias=True) if next_question_intent else None
                ),
                "evaluation": evaluation.model_dump(by_alias=True) if evaluation else None,
                "endReason": end_reason,
                "intent": intent,
            },
            idempotency_key=idempotency_key,
        )
        return InterviewTurnResult.model_validate(data)

    async def _request(
        self,
        method: str,
        path: str,
        context: ToolCallContext,
        *,
        json: dict[str, Any] | None = None,
        idempotency_key: str | None = None,
    ) -> object:
        if not self._service_token:
            raise BusinessToolError(500, "AGENT_INTERNAL_SERVICE_TOKEN is required")

        # JWT 只透传给 Java，不进入 LangGraph state/checkpoint。
        headers = {
            "X-Agent-Service-Token": self._service_token,
            "Authorization": context.authorization,
            "X-Agent-Run-Id": context.run_id,
            "X-Agent-Step-Id": context.step_id,
        }
        if idempotency_key is not None:
            headers["Idempotency-Key"] = idempotency_key

        response = await self._client.request(method, path, headers=headers, json=json)
        response.raise_for_status()
        return self._unwrap_result(response.json())

    @staticmethod
    def _unwrap_result(payload: object) -> object:
        if not isinstance(payload, Mapping):
            raise BusinessToolError(502, "Java Agent 返回了无效响应")
        code = payload.get("code")
        message = payload.get("message")
        if not isinstance(code, int):
            raise BusinessToolError(502, "Java Agent 响应缺少业务状态码")
        if code != 200:
            detail = message if isinstance(message, str) else "业务 Tool 调用失败"
            raise BusinessToolError(code, detail)
        return payload.get("data")
