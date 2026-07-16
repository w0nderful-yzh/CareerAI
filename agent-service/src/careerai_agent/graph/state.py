from typing import Any, TypedDict


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
