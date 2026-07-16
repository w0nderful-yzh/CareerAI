from enum import StrEnum
from typing import Any, Literal

from pydantic import BaseModel, Field, model_validator

from careerai_agent.services.interview import InterviewIntent


class RunStatus(StrEnum):
    PLANNING = "PLANNING"
    RUNNING = "RUNNING"
    WAITING_ASYNC = "WAITING_ASYNC"
    WAITING_APPROVAL = "WAITING_APPROVAL"
    PAUSED = "PAUSED"
    PARTIALLY_COMPLETED = "PARTIALLY_COMPLETED"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"


class StepStatus(StrEnum):
    PENDING = "PENDING"
    IN_PROGRESS = "IN_PROGRESS"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    SKIPPED = "SKIPPED"


class PlanStep(BaseModel):
    id: str
    title: str
    status: StepStatus = StepStatus.PENDING


class CreateRunRequest(BaseModel):
    goal: str = Field(min_length=10, max_length=4000)
    constraints: dict[str, Any] = Field(default_factory=dict)


class SubmitInterviewTurnRequest(BaseModel):
    question_index: int = Field(ge=0, alias="questionIndex")
    answer: str = Field(default="", max_length=12000)
    intent: InterviewIntent = InterviewIntent.AUTO

    model_config = {"populate_by_name": True}

    @model_validator(mode="after")
    def require_answer_for_answer_intent(self) -> "SubmitInterviewTurnRequest":
        requires_answer = self.intent in {InterviewIntent.AUTO, InterviewIntent.ANSWER}
        if requires_answer and not self.answer.strip():
            raise ValueError("answer is required for AUTO or ANSWER intent")
        return self


class CreateInterviewSessionRequest(BaseModel):
    model_config = {"populate_by_name": True}

    resume_text: str | None = Field(default=None, max_length=100_000, alias="resumeText")
    question_count: int = Field(ge=3, le=20, alias="questionCount")
    resume_id: int | None = Field(default=None, gt=0, alias="resumeId")
    force_create: bool = Field(default=False, alias="forceCreate")
    llm_provider: str | None = Field(default=None, max_length=50, alias="llmProvider")
    skill_id: str = Field(min_length=1, max_length=64, alias="skillId")
    difficulty: str = Field(default="mid", pattern="^(junior|mid|senior)$")
    custom_categories: list[dict[str, Any]] = Field(default_factory=list, alias="customCategories")
    jd_text: str | None = Field(default=None, max_length=100_000, alias="jdText")
    job_id: int | None = Field(default=None, gt=0, alias="jobId")
    match_report_id: int | None = Field(default=None, gt=0, alias="matchReportId")
    training_mode: Literal[
        "GENERAL",
        "JOB_TARGETED",
        "FOCUS_DRILL",
        "RESUME_DEFENSE",
    ] = Field(default="GENERAL", alias="trainingMode")
    user_focus: str | None = Field(default=None, max_length=1000, alias="userFocus")


class AgentRun(BaseModel):
    id: str
    user_id: str
    goal: str
    constraints: dict[str, Any]
    status: RunStatus
    plan: list[PlanStep]
    artifacts: list[dict[str, Any]] = Field(default_factory=list)
    errors: list[dict[str, Any]] = Field(default_factory=list)
    pause_reason: str | None = None
    selected_resume_id: int | None = None
    job_id: int | None = None
    match_task_id: int | None = None
    match_report_id: int | None = None
    improvement_plan_id: int | None = None
    poll_count: int = 0


class ApiResult[DataT](BaseModel):
    code: int = 200
    message: str = "success"
    data: DataT
