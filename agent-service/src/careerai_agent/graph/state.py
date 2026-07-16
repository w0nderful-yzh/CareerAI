from dataclasses import dataclass
from typing import Any, TypedDict


@dataclass(frozen=True)
class GraphRuntimeContext:
    """只存在于单次图执行中，避免把用户 JWT 写入 checkpoint。"""

    authorization: str


class CareerAgentState(TypedDict):
    id: str
    user_id: str
    goal: str
    constraints: dict[str, Any]
    status: str
    plan: list[dict[str, Any]]
    artifacts: list[dict[str, Any]]
    errors: list[dict[str, Any]]
    pause_reason: str | None
    selected_resume_id: int | None
    job_id: int | None
    match_task_id: int | None
    match_report_id: int | None
    improvement_plan_id: int | None
    poll_count: int
