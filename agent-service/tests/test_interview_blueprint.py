from careerai_agent.domain.models import CreateInterviewSessionRequest
from careerai_agent.services.blueprint import LangChainInterviewBlueprintPlanner
from careerai_agent.tools.models import (
    AbilityProfileItem,
    InterviewBlueprint,
    InterviewImprovementTask,
    InterviewPlanningContext,
    JdRequirement,
    JobMatchReport,
    RequirementEvidence,
)


def test_cross_session_inputs_keep_distinct_semantics() -> None:
    result = planner()._normalize(
        base_blueprint(),
        request(),
        None,
        InterviewPlanningContext(
            ability_profile=[profile("Redis 一致性", 62, "STABLE", "DECLINING")],
            pending_tasks=[
                InterviewImprovementTask(
                    id=1,
                    session_id="old-session",
                    question_index=0,
                    category="Redis",
                    priority="HIGH",
                    status="TODO",
                    title="补齐事务边界",
                    rationale="上一场缺少提交时序",
                    verification_method="重新回答并说明时序",
                )
            ],
            recent_unverified_targets=["Kubernetes"],
        ),
    )

    assert result.focus_topics[0].startswith("任务复测：Redis")
    assert "薄弱项复测：Redis 一致性" in result.focus_topics
    assert "历史待验证（非弱项）：Kubernetes" in result.focus_topics
    assert "跨场次策略" in result.rationale


def test_conflicting_profile_is_retested_with_a_different_scenario() -> None:
    result = planner()._normalize(
        base_blueprint(),
        request(),
        None,
        InterviewPlanningContext(
            ability_profile=[profile("MySQL 索引", 70, "CONFLICT", "STABLE")],
        ),
    )

    assert "冲突复核：MySQL 索引（换场景验证）" in result.focus_topics


def test_mastered_profile_uses_advanced_scenario_instead_of_basic_repetition() -> None:
    result = planner()._normalize(
        base_blueprint(question_types=["CONCEPT"]),
        request(),
        None,
        InterviewPlanningContext(
            ability_profile=[profile("JVM 调优", 88, "STABLE", "STABLE", confidence=82)],
        ),
    )

    assert "进阶验证：JVM 调优（提高场景复杂度）" in result.focus_topics
    assert "SCENARIO_DESIGN" in result.question_types
    assert "TROUBLESHOOTING" in result.question_types


def test_job_targeted_blueprint_filters_unrelated_history() -> None:
    result = planner()._normalize(
        base_blueprint(),
        request(training_mode="JOB_TARGETED"),
        redis_report(),
        InterviewPlanningContext(
            ability_profile=[
                profile("Redis 一致性", 60, "STABLE", "DECLINING"),
                profile("React 状态管理", 55, "STABLE", "DECLINING"),
            ],
            recent_unverified_targets=["Redis 高可用", "React 性能优化"],
        ),
    )

    assert any("Redis" in topic for topic in result.focus_topics)
    assert not any("React" in topic for topic in result.focus_topics)


def planner() -> LangChainInterviewBlueprintPlanner:
    return LangChainInterviewBlueprintPlanner(object())  # type: ignore[arg-type]


def request(training_mode: str = "GENERAL") -> CreateInterviewSessionRequest:
    return CreateInterviewSessionRequest(
        questionCount=5,
        skillId="java-backend",
        difficulty="mid",
        trainingMode=training_mode,
    )


def base_blueprint(question_types: list[str] | None = None) -> InterviewBlueprint:
    return InterviewBlueprint(
        mode="GENERAL",
        objective="验证 Java 后端能力",
        target_requirement_ids=[],
        focus_topics=["Spring Boot"],
        question_types=question_types or ["CONCEPT", "PROJECT_EVIDENCE"],
        avoid_topics=[],
        difficulty="mid",
        question_count=5,
        max_follow_ups_per_topic=1,
        rationale="根据本轮目标规划。",
    )


def profile(
    display_name: str,
    score: int,
    status: str,
    trend: str,
    *,
    confidence: int = 75,
) -> AbilityProfileItem:
    return AbilityProfileItem(
        dimension="TECHNICAL",
        ability_key=display_name.lower().replace(" ", "-"),
        display_name=display_name,
        score=score,
        confidence=confidence,
        status=status,
        trend=trend,
        observation_count=3,
        session_count=2,
        latest_session_id="old-session",
        latest_question_index=0,
        latest_evidence=["回答证据"],
        latest_missing_points=[],
        last_observed_at="2026-07-16T12:00:00",
    )


def redis_report() -> JobMatchReport:
    requirement = JdRequirement(
        id="REQ-1",
        category="Redis",
        description="具备 Redis 缓存与一致性实践",
        importance="HIGH",
        source_quote="熟悉 Redis",
    )
    return JobMatchReport(
        id=31,
        resume_id=11,
        resume_filename="resume.pdf",
        job_id=22,
        job_title="Java 后端工程师",
        overall_score=75,
        skill_score=75,
        project_score=70,
        keyword_score=80,
        summary="需要验证 Redis 项目证据",
        evidence_mappings=[
            RequirementEvidence(
                requirement=requirement,
                coverage_type="EVIDENCE_GAP",
                confidence=80,
                reasoning="Redis 一致性证据不足",
                recommended_action="通过场景题复测 Redis",
            )
        ],
    )
