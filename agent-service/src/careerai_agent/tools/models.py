from pydantic import BaseModel, ConfigDict, Field


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


class JobMatchTask(JavaToolModel):
    id: int
    status: str
    resume_id: int = Field(alias="resumeId")
    job_id: int = Field(alias="jobId")
    report_id: int | None = Field(default=None, alias="reportId")
    error_message: str | None = Field(default=None, alias="errorMessage")
    report: JobMatchReport | None = None


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


class EmptyInput(BaseModel):
    pass


class ResourceIdInput(BaseModel):
    resource_id: int = Field(gt=0)


class StartJobMatchInput(BaseModel):
    resume_id: int = Field(gt=0)
    job_id: int = Field(gt=0)


class CreateImprovementPlanInput(BaseModel):
    match_report_id: int = Field(gt=0)
