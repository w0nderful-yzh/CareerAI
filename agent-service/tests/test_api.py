from typing import Any

from fastapi.testclient import TestClient

from careerai_agent.config import Settings
from careerai_agent.domain.models import CreateInterviewSessionRequest, PlanStep
from careerai_agent.main import create_app
from careerai_agent.models.config_client import ModelConfigError
from careerai_agent.services.decision import (
    PreparationAction,
    PreparationDecision,
    PreparationStrategy,
)
from careerai_agent.services.interview import (
    DifficultyAdjustment,
    InterviewAction,
    InterviewDecision,
    InterviewIntent,
)
from careerai_agent.tools.client import ToolCallContext
from careerai_agent.tools.models import (
    AbilityProfileItem,
    InterviewBlueprint,
    InterviewCategory,
    InterviewDecisionRecord,
    InterviewImprovementTask,
    InterviewPlanningContext,
    InterviewQuestion,
    InterviewSession,
    InterviewTurnContext,
    InterviewTurnEvaluation,
    InterviewTurnResult,
    JdRequirement,
    JobMatchReport,
    JobMatchTask,
    JobSnapshot,
    NextQuestionIntent,
    PreparationTask,
    RequirementEvidence,
    ResumeDetail,
    ResumeEvidence,
    ResumeImprovementPlan,
    ResumeSummary,
)


class FakeInterviewBlueprintPlanner:
    async def plan(
        self,
        request: CreateInterviewSessionRequest,
        resume: ResumeDetail | None,
        job: JobSnapshot | None,
        report: JobMatchReport | None,
        planning_context: InterviewPlanningContext,
    ) -> InterviewBlueprint:
        assert resume is not None and resume.id == 11
        assert job is not None and job.id == 22
        assert report is not None and report.id == 31
        assert request.user_focus == "强化 Redis 一致性和故障排查"
        assert planning_context.pending_tasks[0].category == "Redis"
        return InterviewBlueprint(
            mode="JOB_TARGETED",
            objective="验证 Redis 项目证据并强化复杂故障定位",
            target_requirement_ids=["REQ-1"],
            focus_topics=["Redis 一致性", "缓存故障定位"],
            question_types=["PROJECT_EVIDENCE", "TROUBLESHOOTING"],
            avoid_topics=[],
            difficulty=request.difficulty,
            question_count=request.question_count,
            max_follow_ups_per_topic=2,
            rationale="岗位要求 Redis 实践，但当前证据缺少规模、指标和一致性说明。",
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
            PlanStep(id="start_job_match", title="启动岗位匹配"),
            PlanStep(id="read_match_report", title="读取匹配报告"),
            PlanStep(id="decide_strategy", title="决定准备策略"),
            PlanStep(id="save_plan", title="保存准备计划"),
        ]


class FailingPlanner:
    async def create_plan(self, goal: str, constraints: dict[str, Any]) -> list[PlanStep]:
        raise ModelConfigError("读取 Agent 模型配置失败")


class FakeDecisionMaker:
    async def decide(
        self,
        goal: str,
        constraints: dict[str, Any],
        report: JobMatchReport,
    ) -> PreparationDecision:
        assert goal
        assert constraints["jobId"] == report.job_id
        assert report.evidence_mappings[0].requirement.id == "REQ-1"
        assert report.evidence_mappings[0].coverage_type == "EVIDENCE_GAP"
        return PreparationDecision(
            action=PreparationAction.CREATE_IMPROVEMENT_PLAN,
            strategy=PreparationStrategy.PROJECT_FIRST,
            rationale="项目证据分低于技能覆盖，应优先补齐可验证的项目成果。",
            prioritized_gaps=["缺少高并发项目证据"],
            supporting_evidence=["项目支撑得分低于技能得分"],
            interview_focus=["项目架构取舍与故障定位"],
        )


class ReportOnlyDecisionMaker:
    async def decide(
        self,
        goal: str,
        constraints: dict[str, Any],
        report: JobMatchReport,
    ) -> PreparationDecision:
        return PreparationDecision(
            action=PreparationAction.COMPLETE_WITH_MATCH_REPORT,
            strategy=PreparationStrategy.BALANCED,
            rationale="用户目标仅要求查看匹配分析，因此不创建新的准备计划。",
            prioritized_gaps=["保留报告中的现有差距"],
            supporting_evidence=["目标中未要求保存改进计划"],
            interview_focus=["后续可按报告差距开展训练"],
        )


class FakeInterviewDecisionMaker:
    async def decide(
        self,
        context: InterviewTurnContext,
        answer: str,
    ) -> InterviewDecision:
        assert context.current_question.question_index == 0
        assert context.asked_questions[0].question_index == 0
        assert context.ability_profile[0].status == "CANDIDATE"
        assert answer == "使用缓存空值并设置较短 TTL"
        return InterviewDecision(
            action=InterviewAction.FOLLOW_UP,
            rationale="回答覆盖了基本方案，但还需要验证一致性和失效策略。",
            answer_score=72,
            feedback="基本方案正确，但缺少缓存更新与并发场景说明。",
            difficulty_adjustment=DifficultyAdjustment.KEEP,
            next_question_intent=NextQuestionIntent(
                question_type="PROJECT_EVIDENCE",
                topic="Redis 一致性",
                requirement_id="REQ-1",
                difficulty="mid",
                follow_up=True,
                parent_question_index=0,
                objective="验证缓存更新与并发失效场景的处理能力",
            ),
            evaluation=InterviewTurnEvaluation(
                answered=True,
                technical_correctness=78,
                technical_depth=65,
                completeness=68,
                scenario_reasoning=70,
                project_understanding=None,
                troubleshooting=66,
                expression_structure=75,
                clarity=80,
                credibility=72,
                job_relevance=76,
                missing_points=["缓存更新一致性"],
                errors=[],
                evidence_snippets=["缓存空值"],
                confidence=82,
            ),
        )

    async def assist(self, context: InterviewTurnContext, intent: InterviewIntent) -> str:
        assert context.current_question.question_index == 0
        assert intent in {InterviewIntent.HINT, InterviewIntent.EXPLAIN}
        return "先区分不存在数据与空值缓存，再考虑 TTL 和并发回源。"


class FakeBusinessToolClient:
    def __init__(self) -> None:
        self.task_poll_count = 0
        self.contexts: list[ToolCallContext] = []
        self.idempotency_keys: list[str] = []
        self.plan_decisions: list[dict[str, object]] = []

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
        strategy: str,
        rationale: str,
        prioritized_gaps: list[str],
        supporting_evidence: list[str],
        interview_focus: list[str],
        context: ToolCallContext,
        idempotency_key: str,
    ) -> ResumeImprovementPlan:
        self.contexts.append(context)
        self.idempotency_keys.append(idempotency_key)
        self.plan_decisions.append(
            {
                "strategy": strategy,
                "rationale": rationale,
                "prioritizedGaps": prioritized_gaps,
                "supportingEvidence": supporting_evidence,
                "interviewFocus": interview_focus,
            }
        )
        return ResumeImprovementPlan(
            id=41,
            match_report_id=match_report_id,
            resume_id=11,
            resume_filename="best.pdf",
            job_id=22,
            job_title="Java 后端工程师",
            readiness_score=85,
            summary="优先补充项目量化结果",
            preparation_tasks=[
                PreparationTask(
                    id="TASK-1",
                    category="PROJECT",
                    title="补充缓存场景的规模和效果指标",
                    priority="P0",
                    suggested_days=2,
                    verification_method="用 STAR 结构完成一次项目复述",
                    status="PENDING",
                    related_requirement_ids=["REQ-1"],
                )
            ],
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
            preparation_tasks=[],
        )

    async def get_interview_turn_context(
        self,
        session_id: str,
        context: ToolCallContext,
    ) -> InterviewTurnContext:
        self.contexts.append(context)
        return InterviewTurnContext(
            session_id=session_id,
            status="IN_PROGRESS",
            difficulty="mid",
            current_question=InterviewQuestion(
                question_index=0,
                question="如何处理缓存穿透？",
                type="REDIS",
                category="Redis",
                follow_up=False,
                requirement_id="REQ-1",
            ),
            asked_questions=[
                InterviewQuestion(
                    question_index=0,
                    question="如何处理缓存穿透？",
                    type="REDIS",
                    category="Redis",
                    follow_up=False,
                    requirement_id="REQ-1",
                )
            ],
            blueprint=InterviewBlueprint(
                mode="JOB_TARGETED",
                objective="验证 Redis 岗位能力",
                target_requirement_ids=["REQ-1"],
                focus_topics=["Redis 一致性"],
                question_types=["PROJECT_EVIDENCE", "TROUBLESHOOTING"],
                avoid_topics=[],
                difficulty="mid",
                question_count=4,
                max_follow_ups_per_topic=2,
                rationale="需要增强 Redis 项目证据。",
            ),
            ability_profile=[
                AbilityProfileItem(
                    dimension="TECHNICAL",
                    ability_key="redis-一致性",
                    display_name="Redis 一致性",
                    score=58,
                    confidence=70,
                    status="CANDIDATE",
                    trend="UNKNOWN",
                    observation_count=1,
                    session_count=1,
                    latest_session_id="previous-session",
                    latest_question_index=2,
                    latest_evidence=["使用延迟双删"],
                    latest_missing_points=["缺少失败补偿"],
                    last_observed_at="2026-07-15T10:00:00",
                )
            ],
            evidence_mappings=self._report().evidence_mappings,
            answered_count=0,
            total_questions=4,
        )

    async def get_interview_planning_context(
        self,
        context: ToolCallContext,
    ) -> InterviewPlanningContext:
        self.contexts.append(context)
        return InterviewPlanningContext(
            ability_profile=[
                AbilityProfileItem(
                    dimension="TECHNICAL",
                    ability_key="redis-一致性",
                    display_name="Redis 一致性",
                    score=58,
                    confidence=76,
                    status="STABLE",
                    trend="DECLINING",
                    observation_count=3,
                    session_count=2,
                    latest_session_id="previous-session",
                    latest_question_index=1,
                    latest_evidence=["延迟双删"],
                    latest_missing_points=["事务边界"],
                    last_observed_at="2026-07-16T12:00:00",
                )
            ],
            pending_tasks=[
                InterviewImprovementTask(
                    id=81,
                    session_id="previous-session",
                    question_index=1,
                    category="Redis",
                    priority="HIGH",
                    status="TODO",
                    title="补齐事务边界并完成复述验证",
                    rationale="上一场回答缺少事务提交时序",
                    verification_method="重新回答并说明事务提交前后时序",
                )
            ],
            recent_unverified_targets=["Kubernetes"],
        )

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
        self.contexts.append(context)
        self.idempotency_keys.append(idempotency_key)
        assert resume_id == 11
        assert job_id == 22
        assert match_report_id == 31
        assert question_count == 6
        assert blueprint.target_requirement_ids == ["REQ-1"]
        return InterviewSession(
            session_id="agent-session-1",
            resume_text=resume_text or "",
            total_questions=2,
            current_question_index=0,
            questions=[
                InterviewQuestion(
                    question_index=0,
                    question="请说明项目中的缓存一致性方案。",
                    type="REDIS",
                    category="Redis",
                    follow_up=False,
                    requirement_id="REQ-1",
                ),
                InterviewQuestion(
                    question_index=1,
                    question="如果发生缓存与数据库短暂不一致，你如何定位？",
                    type="REDIS",
                    category="Redis · 追问1",
                    follow_up=True,
                    parent_question_index=0,
                    requirement_id="REQ-1",
                ),
            ],
            status="CREATED",
            blueprint=blueprint,
        )

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
        self.contexts.append(context)
        self.idempotency_keys.append(idempotency_key)
        if intent != "ANSWER":
            return InterviewTurnResult(
                session_id=session_id,
                completed=intent == "END",
                next_question=None
                if intent == "END"
                else InterviewQuestion(
                    question_index=2,
                    question="解释 MySQL 索引失效场景。",
                    type="MYSQL",
                    category="MySQL",
                    follow_up=False,
                ),
                decision=None,
                answered_count=0,
                total_questions=4,
            )
        assert action == "FOLLOW_UP"
        assert answer_score == 72
        assert evaluation is not None
        assert evaluation.technical_correctness == 78
        assert evaluation.evidence_snippets == ["缓存空值"]
        assert next_question_intent is not None
        assert next_question_intent.follow_up is True
        assert next_question_intent.topic == "Redis 一致性"
        assert end_reason is None
        return InterviewTurnResult(
            session_id=session_id,
            completed=False,
            next_question=InterviewQuestion(
                question_index=1,
                question="缓存更新与数据库写入如何保证一致性？",
                type="REDIS",
                category="Redis · 追问1",
                follow_up=True,
                parent_question_index=0,
                requirement_id="REQ-1",
            ),
            decision=InterviewDecisionRecord(
                question_index=question_index,
                action=action,
                rationale=rationale,
                answer_score=answer_score,
                feedback=feedback,
                difficulty_adjustment=difficulty_adjustment,
                target_question_index=1,
                target_requirement_id="REQ-1",
                created_at="2026-07-16T16:00:00",
            ),
            answered_count=1,
            total_questions=4,
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
            evidence_mappings=[
                RequirementEvidence(
                    requirement=JdRequirement(
                        id="REQ-1",
                        category="CACHE",
                        description="具备 Redis 缓存实践",
                        importance="HIGH",
                        source_quote="熟悉 Redis",
                    ),
                    resume_evidence=[
                        ResumeEvidence(
                            source_type="PROJECT",
                            source_location="CareerAI 项目",
                            quote="使用 Redis 缓存",
                            strength="WEAK",
                        )
                    ],
                    coverage_type="EVIDENCE_GAP",
                    confidence=88,
                    reasoning="缺少规模和结果",
                    recommended_action="补充缓存指标",
                )
            ],
        )


def create_test_app(
    business_client: FakeBusinessToolClient | None = None,
):
    return create_app(
        Settings(_env_file=None),
        planner=FakePlanner(),
        decision_maker=FakeDecisionMaker(),
        interview_decision_maker=FakeInterviewDecisionMaker(),
        interview_blueprint_planner=FakeInterviewBlueprintPlanner(),
        business_client=business_client or FakeBusinessToolClient(),
    )


def test_agent_creates_interview_from_business_context_and_blueprint() -> None:
    business_client = FakeBusinessToolClient()
    app = create_test_app(business_client)

    with TestClient(app) as client:
        app.state.careerai_client = FakeCareerAiClient()
        response = client.post(
            "/api/agent/interviews/sessions",
            headers={"Authorization": "Bearer test-token"},
            json={
                "questionCount": 6,
                "resumeId": 11,
                "forceCreate": True,
                "skillId": "java-backend",
                "difficulty": "senior",
                "jobId": 22,
                "matchReportId": 31,
                "trainingMode": "JOB_TARGETED",
                "userFocus": "强化 Redis 一致性和故障排查",
            },
        )

    assert response.status_code == 201
    data = response.json()["data"]
    assert data["sessionId"] == "agent-session-1"
    assert data["blueprint"]["targetRequirementIds"] == ["REQ-1"]
    assert data["blueprint"]["questionTypes"] == [
        "PROJECT_EVIDENCE",
        "TROUBLESHOOTING",
    ]
    assert [context.step_id for context in business_client.contexts[-4:]] == [
        "load_blueprint_context",
        "load_blueprint_context",
        "load_blueprint_context",
        "create_interview_session",
    ]
    assert business_client.idempotency_keys[-1].endswith(":create_interview_session")


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


def test_adaptive_interview_executes_follow_up_tool() -> None:
    business_client = FakeBusinessToolClient()
    app = create_test_app(business_client)

    with TestClient(app) as client:
        app.state.careerai_client = FakeCareerAiClient()
        response = client.post(
            "/api/agent/interviews/session-1/turns",
            headers={"Authorization": "Bearer test-token"},
            json={
                "questionIndex": 0,
                "answer": "使用缓存空值并设置较短 TTL",
            },
        )

    assert response.status_code == 200
    data = response.json()["data"]
    assert data["decision"]["action"] == "FOLLOW_UP"
    assert data["decision"]["targetRequirementId"] == "REQ-1"
    assert data["nextQuestion"]["questionIndex"] == 1
    assert business_client.idempotency_keys[-1] == "interview:session-1:question:0:question:0"


def test_interview_hint_does_not_submit_or_score_answer() -> None:
    business_client = FakeBusinessToolClient()
    app = create_test_app(business_client)

    with TestClient(app) as client:
        app.state.careerai_client = FakeCareerAiClient()
        response = client.post(
            "/api/agent/interviews/session-1/turns",
            headers={"Authorization": "Bearer test-token"},
            json={"questionIndex": 0, "intent": "HINT"},
        )

    assert response.status_code == 200
    data = response.json()["data"]
    assert data["intent"] == "HINT"
    assert data["decision"] is None
    assert data["nextQuestion"]["questionIndex"] == 0
    assert "TTL" in data["assistantMessage"]
    assert business_client.idempotency_keys == []


def test_text_end_intent_is_not_treated_as_answer() -> None:
    business_client = FakeBusinessToolClient()
    app = create_test_app(business_client)

    with TestClient(app) as client:
        app.state.careerai_client = FakeCareerAiClient()
        response = client.post(
            "/api/agent/interviews/session-1/turns",
            headers={"Authorization": "Bearer test-token"},
            json={"questionIndex": 0, "answer": "我不想继续面试了"},
        )

    assert response.status_code == 200
    data = response.json()["data"]
    assert data["intent"] == "END"
    assert data["completed"] is True
    assert data["decision"] is None
    assert business_client.idempotency_keys[-1].endswith(":end:control")


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
        assert len(run["plan"]) == 5
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
        decision = next(
            artifact
            for artifact in completed["artifacts"]
            if artifact["type"] == "preparation_decision"
        )
        assert decision["strategy"] == "PROJECT_FIRST"
        assert decision["selectedTool"] == "create_resume_improvement_plan"
        plan_artifact = next(
            artifact
            for artifact in completed["artifacts"]
            if artifact["type"] == "resume_improvement_plan"
        )
        assert plan_artifact["preparationTasks"][0]["relatedRequirementIds"] == ["REQ-1"]
        assert business_client.plan_decisions[0]["prioritizedGaps"] == ["缺少高并发项目证据"]
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
        decision_maker=FakeDecisionMaker(),
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


def test_decision_can_finish_without_calling_write_tool() -> None:
    business_client = FakeBusinessToolClient()
    app = create_app(
        Settings(_env_file=None),
        planner=FakePlanner(),
        decision_maker=ReportOnlyDecisionMaker(),
        business_client=business_client,
    )

    with TestClient(app) as client:
        app.state.careerai_client = FakeCareerAiClient()
        created = client.post(
            "/api/agent/runs",
            headers={"Authorization": "Bearer test-token"},
            json={
                "goal": "只分析当前简历与目标岗位的匹配情况，不需要生成新的计划",
                "constraints": {"jobId": 22, "resumeId": 11},
            },
        ).json()["data"]
        completed = client.post(
            f"/api/agent/runs/{created['id']}/resume",
            headers={"Authorization": "Bearer test-token"},
        ).json()["data"]

    assert completed["status"] == "COMPLETED"
    assert completed["improvement_plan_id"] is None
    assert completed["plan"][4]["status"] == "SKIPPED"
    assert business_client.plan_decisions == []
