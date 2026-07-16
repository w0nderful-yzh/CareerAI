from typing import Any

from fastapi.testclient import TestClient

from careerai_agent.config import Settings
from careerai_agent.domain.models import PlanStep
from careerai_agent.main import create_app
from careerai_agent.models.config_client import ModelConfigError
from careerai_agent.tools.client import ToolCallContext
from careerai_agent.tools.models import (
    JobMatchReport,
    JobMatchTask,
    JobSnapshot,
    ResumeDetail,
    ResumeImprovementPlan,
    ResumeSummary,
)


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


class FailingPlanner:
    async def create_plan(self, goal: str, constraints: dict[str, Any]) -> list[PlanStep]:
        raise ModelConfigError("读取 Agent 模型配置失败")


class FakeBusinessToolClient:
    def __init__(self) -> None:
        self.task_poll_count = 0
        self.contexts: list[ToolCallContext] = []
        self.idempotency_keys: list[str] = []

    async def list_resumes(self, context: ToolCallContext) -> list[ResumeSummary]:
        self.contexts.append(context)
        return [
            ResumeSummary(id=10, filename="old.pdf", latest_score=70),
            ResumeSummary(id=11, filename="best.pdf", latest_score=92),
        ]

    async def get_resume_detail(
        self,
        resume_id: int,
        context: ToolCallContext,
    ) -> ResumeDetail:
        self.contexts.append(context)
        return ResumeDetail(id=resume_id, filename="best.pdf", latest_score=92)

    async def get_job(self, job_id: int, context: ToolCallContext) -> JobSnapshot:
        self.contexts.append(context)
        return JobSnapshot(id=job_id, title="Java 后端工程师", jd_text="Java, Spring")

    async def start_job_match(
        self,
        resume_id: int,
        job_id: int,
        context: ToolCallContext,
        idempotency_key: str,
    ) -> JobMatchTask:
        self.contexts.append(context)
        self.idempotency_keys.append(idempotency_key)
        return JobMatchTask(id=91, status="PENDING", resume_id=resume_id, job_id=job_id)

    async def get_job_match_task(
        self,
        task_id: int,
        context: ToolCallContext,
    ) -> JobMatchTask:
        self.contexts.append(context)
        self.task_poll_count += 1
        if self.task_poll_count == 1:
            return JobMatchTask(id=task_id, status="PROCESSING", resume_id=11, job_id=22)
        report = self._report()
        return JobMatchTask(
            id=task_id,
            status="COMPLETED",
            resume_id=11,
            job_id=22,
            report_id=report.id,
            report=report,
        )

    async def get_match_report(
        self,
        report_id: int,
        context: ToolCallContext,
    ) -> JobMatchReport:
        self.contexts.append(context)
        return self._report()

    async def create_improvement_plan(
        self,
        match_report_id: int,
        context: ToolCallContext,
        idempotency_key: str,
    ) -> ResumeImprovementPlan:
        self.contexts.append(context)
        self.idempotency_keys.append(idempotency_key)
        return ResumeImprovementPlan(
            id=41,
            match_report_id=match_report_id,
            resume_id=11,
            resume_filename="best.pdf",
            job_id=22,
            job_title="Java 后端工程师",
            readiness_score=85,
            summary="优先补充项目量化结果",
        )

    async def get_improvement_plan(
        self,
        plan_id: int,
        context: ToolCallContext,
    ) -> ResumeImprovementPlan:
        self.contexts.append(context)
        return ResumeImprovementPlan(
            id=plan_id,
            match_report_id=31,
            resume_id=11,
            resume_filename="best.pdf",
            job_id=22,
            job_title="Java 后端工程师",
            readiness_score=85,
            summary="优先补充项目量化结果",
        )

    @staticmethod
    def _report() -> JobMatchReport:
        return JobMatchReport(
            id=31,
            resume_id=11,
            resume_filename="best.pdf",
            job_id=22,
            job_title="Java 后端工程师",
            overall_score=82,
            skill_score=85,
            project_score=78,
            keyword_score=80,
            summary="总体匹配",
        )


def create_test_app(
    business_client: FakeBusinessToolClient | None = None,
):
    return create_app(
        Settings(_env_file=None),
        planner=FakePlanner(),
        business_client=business_client or FakeBusinessToolClient(),
    )


def test_health_reports_memory_checkpointer() -> None:
    app = create_test_app()

    with TestClient(app) as client:
        response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["data"] == {
        "status": "UP",
        "environment": "local",
        "checkpointer": "memory",
    }


def test_run_is_scoped_to_current_user() -> None:
    business_client = FakeBusinessToolClient()
    app = create_test_app(business_client)

    with TestClient(app) as client:
        fake_client = FakeCareerAiClient()
        app.state.careerai_client = fake_client
        created = client.post(
            "/api/agent/runs",
            headers={"Authorization": "Bearer test-token"},
            json={
                "goal": "为 Java 后端岗位生成求职准备计划",
                "constraints": {"days": 3, "jobId": 22},
            },
        )
        assert created.status_code == 201
        run = created.json()["data"]
        assert run["status"] == "WAITING_ASYNC"
        assert run["pause_reason"] == "JOB_MATCH_IN_PROGRESS"
        assert run["selected_resume_id"] == 11
        assert run["match_task_id"] == 91
        assert len(run["plan"]) == 4
        assert business_client.idempotency_keys == [f"{run['id']}:start_job_match"]
        assert all(
            context.authorization == "Bearer test-token" for context in business_client.contexts
        )

        resumed = client.post(
            f"/api/agent/runs/{run['id']}/resume",
            headers={"Authorization": "Bearer test-token"},
        )
        assert resumed.status_code == 200
        completed = resumed.json()["data"]
        assert completed["status"] == "COMPLETED"
        assert completed["match_report_id"] == 31
        assert completed["improvement_plan_id"] == 41
        assert completed["poll_count"] == 2
        assert business_client.idempotency_keys == [
            f"{run['id']}:start_job_match",
            f"{run['id']}:create_improvement_plan",
        ]

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
    app = create_test_app()

    with TestClient(app) as client:
        response = client.post(
            "/api/agent/runs",
            json={"goal": "为 Java 后端岗位生成求职准备计划"},
        )

    assert response.status_code == 401


def test_run_pauses_when_job_id_is_missing() -> None:
    app = create_test_app()

    with TestClient(app) as client:
        app.state.careerai_client = FakeCareerAiClient()
        response = client.post(
            "/api/agent/runs",
            headers={"Authorization": "Bearer test-token"},
            json={"goal": "为 Java 后端岗位生成求职准备计划"},
        )

    assert response.status_code == 201
    assert response.json()["data"]["status"] == "PAUSED"
    assert response.json()["data"]["pause_reason"] == "MISSING_JOB_ID"


def test_model_config_error_returns_structured_response() -> None:
    app = create_app(
        Settings(_env_file=None),
        planner=FailingPlanner(),
        business_client=FakeBusinessToolClient(),
    )

    with TestClient(app) as client:
        app.state.careerai_client = FakeCareerAiClient()
        response = client.post(
            "/api/agent/runs",
            headers={"Authorization": "Bearer test-token"},
            json={
                "goal": "为 Java 后端岗位生成求职准备计划",
                "constraints": {"jobId": 22},
            },
        )

    assert response.status_code == 502
    assert response.json() == {
        "code": 9501,
        "message": "读取 Agent 模型配置失败",
        "data": None,
    }
