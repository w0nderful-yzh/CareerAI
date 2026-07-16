from enum import StrEnum
from typing import Any

from pydantic import BaseModel, Field


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
