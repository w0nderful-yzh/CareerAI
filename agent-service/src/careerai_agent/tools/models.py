from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, StringConstraints

ToolDecisionItem = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1, max_length=300),
]


class JavaToolModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True)


class ResumeSummary(JavaToolModel):
    id: int
    filename: str
    latest_score: int | None = Field(default=None, alias="latestScore")
    analyze_status: str | None = Field(default=None, alias="analyzeStatus")


class ResumeDetail(JavaToolModel):
    id: int
    filename: str
    resume_text: str | None = Field(default=None, alias="resumeText")
    analyze_status: str | None = Field(default=None, alias="analyzeStatus")
    latest_score: int | None = Field(default=None, alias="latestScore")
    latest_summary: str | None = Field(default=None, alias="latestSummary")
    strengths: list[str] = Field(default_factory=list)


class JobSnapshot(JavaToolModel):
    id: int
    title: str
    company: str | None = None
    location: str | None = None
    status: str | None = None
    jd_text: str = Field(alias="jdText")


class JdRequirement(JavaToolModel):
    id: str
    category: str
    description: str
    importance: str
    source_quote: str = Field(alias="sourceQuote")


class ResumeEvidence(JavaToolModel):
    source_type: str = Field(alias="sourceType")
    source_location: str = Field(alias="sourceLocation")
    quote: str
    strength: str


class RequirementEvidence(JavaToolModel):
    requirement: JdRequirement
    resume_evidence: list[ResumeEvidence] = Field(default_factory=list, alias="resumeEvidence")
    coverage_type: str = Field(alias="coverageType")
    confidence: int
    reasoning: str
    recommended_action: str = Field(alias="recommendedAction")


class JobMatchReport(JavaToolModel):
    id: int
    resume_id: int = Field(alias="resumeId")
    resume_filename: str = Field(alias="resumeFilename")
    job_id: int = Field(alias="jobId")
    job_title: str = Field(alias="jobTitle")
    overall_score: int = Field(alias="overallScore")
    skill_score: int = Field(alias="skillScore")
    project_score: int = Field(alias="projectScore")
    keyword_score: int = Field(alias="keywordScore")
    summary: str
    matched_highlights: list[str] = Field(default_factory=list, alias="matchedHighlights")
    gaps: list[str] = Field(default_factory=list)
    action_items: list[str] = Field(default_factory=list, alias="actionItems")
    evidence_mappings: list[RequirementEvidence] = Field(
        default_factory=list,
        alias="evidenceMappings",
    )


class JobMatchTask(JavaToolModel):
    id: int
    status: str
    resume_id: int = Field(alias="resumeId")
    job_id: int = Field(alias="jobId")
    report_id: int | None = Field(default=None, alias="reportId")
    error_message: str | None = Field(default=None, alias="errorMessage")
    report: JobMatchReport | None = None


class PreparationTask(JavaToolModel):
    id: str
    category: str
    title: str
    priority: str
    suggested_days: int = Field(alias="suggestedDays")
    verification_method: str = Field(alias="verificationMethod")
    status: str
    related_requirement_ids: list[str] = Field(
        default_factory=list,
        alias="relatedRequirementIds",
    )


class ResumeImprovementPlan(JavaToolModel):
    id: int
    match_report_id: int = Field(alias="matchReportId")
    resume_id: int = Field(alias="resumeId")
    resume_filename: str = Field(alias="resumeFilename")
    job_id: int = Field(alias="jobId")
    job_title: str = Field(alias="jobTitle")
    readiness_score: int = Field(alias="readinessScore")
    summary: str
    priority_fixes: list[str] = Field(default_factory=list, alias="priorityFixes")
    resume_rewrite_bullets: list[str] = Field(default_factory=list, alias="resumeRewriteBullets")
    project_upgrade_tasks: list[str] = Field(default_factory=list, alias="projectUpgradeTasks")
    interview_practice_tasks: list[str] = Field(
        default_factory=list,
        alias="interviewPracticeTasks",
    )
    learning_tasks: list[str] = Field(default_factory=list, alias="learningTasks")
    preparation_tasks: list[PreparationTask] = Field(
        default_factory=list,
        alias="preparationTasks",
    )


class InterviewQuestion(JavaToolModel):
    question_index: int = Field(alias="questionIndex")
    question: str
    type: str
    category: str
    topic_summary: str | None = Field(default=None, alias="topicSummary")
    user_answer: str | None = Field(default=None, alias="userAnswer")
    score: int | None = None
    feedback: str | None = None
    follow_up: bool = Field(alias="followUp")
    parent_question_index: int | None = Field(default=None, alias="parentQuestionIndex")
    requirement_id: str | None = Field(default=None, alias="requirementId")


class AbilityProfileItem(JavaToolModel):
    dimension: str
    ability_key: str = Field(alias="abilityKey")
    display_name: str = Field(alias="displayName")
    score: int = Field(ge=0, le=100)
    confidence: int = Field(ge=0, le=100)
    status: str
    trend: str
    observation_count: int = Field(ge=1, alias="observationCount")
    session_count: int = Field(ge=1, alias="sessionCount")
    latest_session_id: str = Field(alias="latestSessionId")
    latest_question_index: int = Field(ge=0, alias="latestQuestionIndex")
    latest_evidence: list[str] = Field(default_factory=list, alias="latestEvidence")
    latest_missing_points: list[str] = Field(default_factory=list, alias="latestMissingPoints")
    last_observed_at: str = Field(alias="lastObservedAt")


class InterviewImprovementTask(JavaToolModel):
    id: int
    session_id: str = Field(alias="sessionId")
    question_index: int = Field(ge=0, alias="questionIndex")
    category: str
    priority: str
    status: str
    title: str
    rationale: str
    verification_method: str = Field(alias="verificationMethod")


class InterviewPlanningContext(JavaToolModel):
    ability_profile: list[AbilityProfileItem] = Field(default_factory=list, alias="abilityProfile")
    pending_tasks: list[InterviewImprovementTask] = Field(
        default_factory=list,
        alias="pendingTasks",
    )
    recent_unverified_targets: list[str] = Field(
        default_factory=list,
        alias="recentUnverifiedTargets",
    )


class InterviewTurnContext(JavaToolModel):
    session_id: str = Field(alias="sessionId")
    status: str
    difficulty: str
    current_question: InterviewQuestion = Field(alias="currentQuestion")
    asked_questions: list[InterviewQuestion] = Field(default_factory=list, alias="askedQuestions")
    blueprint: "InterviewBlueprint"
    ability_profile: list[AbilityProfileItem] = Field(default_factory=list, alias="abilityProfile")
    evidence_mappings: list[RequirementEvidence] = Field(
        default_factory=list,
        alias="evidenceMappings",
    )
    answered_count: int = Field(alias="answeredCount")
    total_questions: int = Field(alias="totalQuestions")


class InterviewDecisionRecord(JavaToolModel):
    question_index: int = Field(alias="questionIndex")
    action: str
    rationale: str
    answer_score: int = Field(alias="answerScore")
    feedback: str
    difficulty_adjustment: str = Field(alias="difficultyAdjustment")
    target_question_index: int | None = Field(default=None, alias="targetQuestionIndex")
    target_requirement_id: str | None = Field(default=None, alias="targetRequirementId")
    created_at: str = Field(alias="createdAt")


class InterviewTurnResult(JavaToolModel):
    session_id: str = Field(alias="sessionId")
    completed: bool
    next_question: InterviewQuestion | None = Field(default=None, alias="nextQuestion")
    decision: InterviewDecisionRecord | None = None
    answered_count: int = Field(alias="answeredCount")
    total_questions: int = Field(alias="totalQuestions")
    intent: str = "ANSWER"
    assistant_message: str | None = Field(default=None, alias="assistantMessage")


class InterviewTurnEvaluation(JavaToolModel):
    answered: bool
    technical_correctness: int | None = Field(
        default=None,
        ge=0,
        le=100,
        alias="technicalCorrectness",
    )
    technical_depth: int | None = Field(default=None, ge=0, le=100, alias="technicalDepth")
    completeness: int | None = Field(default=None, ge=0, le=100)
    scenario_reasoning: int | None = Field(default=None, ge=0, le=100, alias="scenarioReasoning")
    project_understanding: int | None = Field(
        default=None,
        ge=0,
        le=100,
        alias="projectUnderstanding",
    )
    troubleshooting: int | None = Field(default=None, ge=0, le=100)
    expression_structure: int | None = Field(
        default=None,
        ge=0,
        le=100,
        alias="expressionStructure",
    )
    clarity: int | None = Field(default=None, ge=0, le=100)
    credibility: int | None = Field(default=None, ge=0, le=100)
    job_relevance: int | None = Field(default=None, ge=0, le=100, alias="jobRelevance")
    missing_points: list[str] = Field(default_factory=list, max_length=8, alias="missingPoints")
    errors: list[str] = Field(default_factory=list, max_length=8)
    evidence_snippets: list[str] = Field(
        default_factory=list,
        max_length=5,
        alias="evidenceSnippets",
    )
    confidence: int = Field(ge=0, le=100)


class NextQuestionIntent(JavaToolModel):
    question_type: str = Field(min_length=1, max_length=40, alias="questionType")
    topic: str = Field(min_length=1, max_length=120)
    requirement_id: str | None = Field(default=None, alias="requirementId")
    difficulty: str
    follow_up: bool = Field(alias="followUp")
    parent_question_index: int | None = Field(default=None, ge=0, alias="parentQuestionIndex")
    objective: str = Field(min_length=5, max_length=500)


class InterviewBlueprint(JavaToolModel):
    mode: str
    objective: str
    target_requirement_ids: list[str] = Field(
        default_factory=list,
        alias="targetRequirementIds",
    )
    focus_topics: list[str] = Field(default_factory=list, alias="focusTopics")
    question_types: list[str] = Field(default_factory=list, alias="questionTypes")
    avoid_topics: list[str] = Field(default_factory=list, alias="avoidTopics")
    difficulty: str
    question_count: int = Field(ge=3, le=20, alias="questionCount")
    max_follow_ups_per_topic: int = Field(ge=0, le=2, alias="maxFollowUpsPerTopic")
    rationale: str


class InterviewCategory(JavaToolModel):
    key: str
    label: str
    priority: str
    ref: str | None = None
    shared: bool | None = None


class InterviewSession(JavaToolModel):
    session_id: str = Field(alias="sessionId")
    resume_text: str = Field(alias="resumeText")
    total_questions: int = Field(alias="totalQuestions")
    current_question_index: int = Field(alias="currentQuestionIndex")
    questions: list[InterviewQuestion]
    status: str
    blueprint: InterviewBlueprint
    end_reason: str | None = Field(default=None, alias="endReason")
    completion_type: str | None = Field(default=None, alias="completionType")
    covered_targets: list[str] = Field(default_factory=list, alias="coveredTargets")
    unverified_targets: list[str] = Field(default_factory=list, alias="unverifiedTargets")


class EmptyInput(BaseModel):
    pass


class ResourceIdInput(BaseModel):
    resource_id: int = Field(gt=0)


class StartJobMatchInput(BaseModel):
    resume_id: int = Field(gt=0)
    job_id: int = Field(gt=0)


class CreateImprovementPlanInput(BaseModel):
    match_report_id: int = Field(gt=0)
    strategy: str = Field(min_length=1, max_length=40)
    rationale: str = Field(min_length=1, max_length=500)
    prioritized_gaps: list[ToolDecisionItem] = Field(min_length=1, max_length=5)
    supporting_evidence: list[ToolDecisionItem] = Field(min_length=1, max_length=5)
    interview_focus: list[ToolDecisionItem] = Field(min_length=1, max_length=5)


class CreateInterviewSessionInput(BaseModel):
    resume_text: str | None = None
    question_count: int = Field(ge=3, le=20)
    resume_id: int | None = Field(default=None, gt=0)
    force_create: bool = False
    llm_provider: str | None = None
    skill_id: str = Field(min_length=1, max_length=64)
    difficulty: str
    custom_categories: list[InterviewCategory] = Field(default_factory=list)
    jd_text: str | None = None
    job_id: int | None = Field(default=None, gt=0)
    match_report_id: int | None = Field(default=None, gt=0)
    blueprint: InterviewBlueprint


class InterviewSessionInput(BaseModel):
    session_id: str = Field(min_length=1, max_length=36)


class ApplyInterviewTurnInput(BaseModel):
    session_id: str = Field(min_length=1, max_length=36)
    question_index: int = Field(ge=0)
    answer: str = Field(min_length=1, max_length=12000)
    action: str
    rationale: str = Field(min_length=1, max_length=500)
    answer_score: int = Field(ge=0, le=100)
    feedback: str = Field(min_length=1, max_length=1000)
    difficulty_adjustment: str
    next_question_intent: NextQuestionIntent | None = None
    evaluation: InterviewTurnEvaluation
    end_reason: str | None = None
