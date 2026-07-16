from typing import Any

from fastapi.testclient import TestClient

from careerai_agent.config import Settings
from careerai_agent.domain.models import PlanStep
from careerai_agent.main import create_app


class FakeCareerAiClient:
    def __init__(self, user_id: int = 7) -> None:
        self.user_id = user_id

    async def get_current_user(self, authorization: str) -> dict[str, Any]:
        assert authorization == "Bearer test-token"
        return {"id": self.user_id, "username": "tester"}


class FakePlanner:
    async def create_plan(self, goal: str, constraints: dict[str, Any]) -> list[PlanStep]:
        return [
            PlanStep(id="load_context", title="加载业务上下文"),
            PlanStep(id="build_plan", title="生成执行计划"),
            PlanStep(id="execute_tools", title="调用业务工具"),
            PlanStep(id="verify_result", title="验证业务结果"),
        ]


def test_health_reports_memory_checkpointer() -> None:
    app = create_app(Settings(_env_file=None), planner=FakePlanner())

    with TestClient(app) as client:
        response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["data"] == {
        "status": "UP",
        "environment": "local",
        "checkpointer": "memory",
    }


def test_run_is_scoped_to_current_user() -> None:
    app = create_app(Settings(_env_file=None), planner=FakePlanner())

    with TestClient(app) as client:
        fake_client = FakeCareerAiClient()
        app.state.careerai_client = fake_client
        created = client.post(
            "/api/agent/runs",
            headers={"Authorization": "Bearer test-token"},
            json={"goal": "为 Java 后端岗位生成求职准备计划", "constraints": {"days": 3}},
        )
        assert created.status_code == 201
        run = created.json()["data"]
        assert run["status"] == "PAUSED"
        assert run["pause_reason"] == "BUSINESS_TOOLS_NOT_CONFIGURED"
        assert len(run["plan"]) == 4

        fetched = client.get(
            f"/api/agent/runs/{run['id']}",
            headers={"Authorization": "Bearer test-token"},
        )
        assert fetched.status_code == 200
        assert fetched.json()["data"]["id"] == run["id"]

        fake_client.user_id = 8
        hidden = client.get(
            f"/api/agent/runs/{run['id']}",
            headers={"Authorization": "Bearer test-token"},
        )
        assert hidden.status_code == 404


def test_run_requires_bearer_token() -> None:
    app = create_app(Settings(_env_file=None), planner=FakePlanner())

    with TestClient(app) as client:
        response = client.post(
            "/api/agent/runs",
            json={"goal": "为 Java 后端岗位生成求职准备计划"},
        )

    assert response.status_code == 401
