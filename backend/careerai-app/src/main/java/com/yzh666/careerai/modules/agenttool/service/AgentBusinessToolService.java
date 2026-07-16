package com.yzh666.careerai.modules.agenttool.service;

import com.yzh666.careerai.common.agent.tool.AgentJdRequirement;
import com.yzh666.careerai.common.agent.tool.AgentAbilityProfileItem;
import com.yzh666.careerai.common.agent.tool.AgentCreateInterviewSessionCommand;
import com.yzh666.careerai.common.agent.tool.AgentInterviewBlueprint;
import com.yzh666.careerai.common.agent.tool.AgentInterviewQuestion;
import com.yzh666.careerai.common.agent.tool.AgentInterviewImprovementTask;
import com.yzh666.careerai.common.agent.tool.AgentInterviewPlanningContext;
import com.yzh666.careerai.common.agent.tool.AgentInterviewSession;
import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnCommand;
import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnContext;
import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnResult;
import com.yzh666.careerai.common.agent.tool.AgentJobMatchCommand;
import com.yzh666.careerai.common.agent.tool.AgentJobMatchReport;
import com.yzh666.careerai.common.agent.tool.AgentJobMatchTask;
import com.yzh666.careerai.common.agent.tool.AgentJobSnapshot;
import com.yzh666.careerai.common.agent.tool.AgentPreparationTask;
import com.yzh666.careerai.common.agent.tool.AgentResumeDetail;
import com.yzh666.careerai.common.agent.tool.AgentResumeEvidence;
import com.yzh666.careerai.common.agent.tool.AgentResumeImprovementPlan;
import com.yzh666.careerai.common.agent.tool.AgentResumeImprovementPlanCommand;
import com.yzh666.careerai.common.agent.tool.AgentResumeSummary;
import com.yzh666.careerai.common.agent.tool.AgentRequirementEvidence;
import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.modules.job.dto.JobDTO;
import com.yzh666.careerai.modules.interview.model.InterviewQuestionDTO;
import com.yzh666.careerai.modules.interview.model.CreateInterviewRequest;
import com.yzh666.careerai.modules.interview.model.InterviewBlueprintDTO;
import com.yzh666.careerai.modules.interview.model.InterviewSessionDTO;
import com.yzh666.careerai.modules.interview.skill.InterviewSkillService.CategoryDTO;
import com.yzh666.careerai.modules.interview.service.InterviewPersistenceService;
import com.yzh666.careerai.modules.interview.service.AbilityProfileService;
import com.yzh666.careerai.modules.interview.service.InterviewSessionService;
import com.yzh666.careerai.modules.interview.service.InterviewClosureService;
import com.yzh666.careerai.modules.job.service.JobService;
import com.yzh666.careerai.modules.jobmatch.dto.CreateJobMatchRequest;
import com.yzh666.careerai.modules.jobmatch.dto.JobMatchReportDTO;
import com.yzh666.careerai.modules.jobmatch.dto.JobMatchTaskDTO;
import com.yzh666.careerai.modules.jobmatch.service.JobMatchService;
import com.yzh666.careerai.modules.jobmatch.service.JobMatchTaskService;
import com.yzh666.careerai.modules.resume.model.ResumeDetailDTO;
import com.yzh666.careerai.modules.resume.model.ResumeListItemDTO;
import com.yzh666.careerai.modules.resume.service.ResumeHistoryService;
import com.yzh666.careerai.modules.resumeplan.dto.ResumeImprovementPlanDTO;
import com.yzh666.careerai.modules.resumeplan.service.ResumeImprovementPlanService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 把现有业务 Service 适配成稳定的 Agent Tool 契约。
 * 这里不绕过领域服务，因此用户隔离、业务校验和异常语义仍保持一致。
 */
@Service
@RequiredArgsConstructor
public class AgentBusinessToolService {

  private final ResumeHistoryService resumeHistoryService;
  private final JobService jobService;
  private final JobMatchTaskService jobMatchTaskService;
  private final JobMatchService jobMatchService;
  private final ResumeImprovementPlanService improvementPlanService;
  private final InterviewSessionService interviewSessionService;
  private final InterviewPersistenceService interviewPersistenceService;
  private final AbilityProfileService abilityProfileService;
  private final InterviewClosureService interviewClosureService;

  public List<AgentResumeSummary> listResumes() {
    return resumeHistoryService.getAllResumes().stream()
        .map(this::toResumeSummary)
        .toList();
  }

  public AgentResumeDetail getResumeDetail(Long resumeId) {
    ResumeDetailDTO detail = resumeHistoryService.getResumeDetail(resumeId);
    ResumeDetailDTO.AnalysisHistoryDTO latest = detail.analyses().isEmpty()
        ? null
        : detail.analyses().getFirst();
    return new AgentResumeDetail(
        detail.id(),
        detail.filename(),
        detail.resumeText(),
        enumName(detail.analyzeStatus()),
        latest == null ? null : latest.overallScore(),
        latest == null ? null : latest.summary(),
        latest == null ? List.of() : latest.strengths()
    );
  }

  public AgentJobSnapshot getJob(Long jobId) {
    JobDTO job = jobService.getJob(jobId);
    return new AgentJobSnapshot(
        job.id(),
        job.title(),
        job.company(),
        job.location(),
        enumName(job.status()),
        job.jdText(),
        job.updatedAt()
    );
  }

  public AgentJobMatchTask startJobMatch(
      AgentJobMatchCommand command,
      String idempotencyKey
  ) {
    return toJobMatchTask(jobMatchTaskService.createTaskIdempotently(
        new CreateJobMatchRequest(command.resumeId(), command.jobId()),
        idempotencyKey
    ));
  }

  public AgentJobMatchTask getJobMatchTask(Long taskId) {
    return toJobMatchTask(jobMatchTaskService.getTask(taskId));
  }

  public AgentJobMatchReport getMatchReport(Long reportId) {
    return toJobMatchReport(jobMatchService.getReport(reportId));
  }

  public AgentResumeImprovementPlan createImprovementPlan(
      AgentResumeImprovementPlanCommand command,
      String idempotencyKey
  ) {
    return toImprovementPlan(improvementPlanService.createPlanIdempotently(
        command,
        idempotencyKey
    ));
  }

  public AgentResumeImprovementPlan getImprovementPlan(Long planId) {
    return toImprovementPlan(improvementPlanService.getPlan(planId));
  }

  /**
   * 执行 Agent 已规划好的面试蓝图。Java 仍负责用户隔离、参数校验、出题和持久化。
   */
  public AgentInterviewSession createInterviewSession(
      AgentCreateInterviewSessionCommand command,
      String idempotencyKey
  ) {
    List<CategoryDTO> categories = command.customCategories() == null
        ? List.of()
        : command.customCategories().stream()
            .map(category -> new CategoryDTO(
                category.key(),
                category.label(),
                category.priority(),
                category.ref(),
                category.shared()))
            .toList();
    InterviewSessionDTO session = interviewSessionService.createSessionIdempotently(new CreateInterviewRequest(
        command.resumeText(),
        command.questionCount(),
        command.resumeId(),
        command.forceCreate(),
        command.llmProvider(),
        command.skillId(),
        command.difficulty(),
        categories,
        command.jdText(),
        command.jobId(),
        command.matchReportId(),
        toInterviewBlueprint(command.blueprint())
    ), idempotencyKey);
    return toInterviewSession(session);
  }

  public AgentInterviewTurnContext getInterviewTurnContext(String sessionId) {
    InterviewSessionDTO session = interviewSessionService.getSession(sessionId);
    if (session.status() == InterviewSessionDTO.SessionStatus.COMPLETED
        || session.status() == InterviewSessionDTO.SessionStatus.EVALUATED
        || session.currentQuestionIndex() >= session.questions().size()) {
      throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED);
    }
    var entity = interviewPersistenceService.findBySessionId(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
    List<AgentRequirementEvidence> evidenceMappings = entity.getMatchReportId() == null
        ? List.of()
        : toJobMatchReport(jobMatchService.getReport(entity.getMatchReportId())).evidenceMappings();
    int answeredCount = (int) session.questions().stream()
        .filter(question -> question.userAnswer() != null && !question.userAnswer().isBlank())
        .count();
    return new AgentInterviewTurnContext(
        sessionId,
        session.status().name(),
        entity.getDifficulty(),
        toInterviewQuestion(session.questions().get(session.currentQuestionIndex())),
        session.questions().stream()
            .map(this::toInterviewQuestion)
            .toList(),
        toAgentInterviewBlueprint(session.blueprint()),
        abilityProfileService.getCurrentProfile().stream()
            .map(this::toAgentAbilityProfile)
            .toList(),
        evidenceMappings,
        answeredCount,
        session.totalQuestions()
    );
  }

  /** 新面试规划前读取跨场次事实，由 Python Agent 决定如何安排复测。 */
  public AgentInterviewPlanningContext getInterviewPlanningContext() {
    var context = interviewClosureService.getPlanningContext();
    return new AgentInterviewPlanningContext(
        context.abilityProfile().stream().map(this::toAgentAbilityProfile).toList(),
        context.pendingTasks().stream()
            .map(task -> new AgentInterviewImprovementTask(
                task.id(),
                task.sessionId(),
                task.questionIndex(),
                task.category(),
                task.priority(),
                task.status(),
                task.title(),
                task.rationale(),
                task.verificationMethod()))
            .toList(),
        context.recentUnverifiedTargets()
    );
  }

  public AgentInterviewTurnResult applyInterviewTurn(
      String sessionId,
      AgentInterviewTurnCommand command
  ) {
    return interviewSessionService.applyAdaptiveTurn(sessionId, command);
  }

  private AgentResumeSummary toResumeSummary(ResumeListItemDTO resume) {
    return new AgentResumeSummary(
        resume.id(),
        resume.filename(),
        resume.latestScore(),
        enumName(resume.analyzeStatus()),
        resume.uploadedAt()
    );
  }

  private AgentJobMatchTask toJobMatchTask(JobMatchTaskDTO task) {
    return new AgentJobMatchTask(
        task.id(),
        enumName(task.status()),
        task.resumeId(),
        task.jobId(),
        task.reportId(),
        task.errorMessage(),
        task.report() == null ? null : toJobMatchReport(task.report()),
        task.createdAt(),
        task.updatedAt()
    );
  }

  private AgentJobMatchReport toJobMatchReport(JobMatchReportDTO report) {
    return new AgentJobMatchReport(
        report.id(),
        report.resumeId(),
        report.resumeFilename(),
        report.jobId(),
        report.jobTitle(),
        report.overallScore(),
        report.skillScore(),
        report.projectScore(),
        report.keywordScore(),
        report.summary(),
        report.matchedHighlights(),
        report.gaps(),
        report.actionItems(),
        report.evidenceMappings().stream()
            .map(mapping -> new AgentRequirementEvidence(
                new AgentJdRequirement(
                    mapping.requirement().id(),
                    mapping.requirement().category(),
                    mapping.requirement().description(),
                    mapping.requirement().importance(),
                    mapping.requirement().sourceQuote()
                ),
                mapping.resumeEvidence().stream()
                    .map(evidence -> new AgentResumeEvidence(
                        evidence.sourceType(),
                        evidence.sourceLocation(),
                        evidence.quote(),
                        evidence.strength()
                    ))
                    .toList(),
                mapping.coverageType(),
                mapping.confidence(),
                mapping.reasoning(),
                mapping.recommendedAction()
            ))
            .toList(),
        report.createdAt()
    );
  }

  private AgentResumeImprovementPlan toImprovementPlan(ResumeImprovementPlanDTO plan) {
    return new AgentResumeImprovementPlan(
        plan.id(),
        plan.matchReportId(),
        plan.resumeId(),
        plan.resumeFilename(),
        plan.jobId(),
        plan.jobTitle(),
        plan.readinessScore(),
        plan.summary(),
        plan.priorityFixes(),
        plan.resumeRewriteBullets(),
        plan.projectUpgradeTasks(),
        plan.interviewPracticeTasks(),
        plan.learningTasks(),
        plan.preparationTasks().stream()
            .map(task -> new AgentPreparationTask(
                task.id(),
                task.category(),
                task.title(),
                task.priority(),
                task.suggestedDays(),
                task.verificationMethod(),
                task.status(),
                task.relatedRequirementIds()
            ))
            .toList(),
        plan.createdAt()
    );
  }

  private AgentInterviewQuestion toInterviewQuestion(InterviewQuestionDTO question) {
    return new AgentInterviewQuestion(
        question.questionIndex(),
        question.question(),
        question.type(),
        question.category(),
        question.topicSummary(),
        question.userAnswer(),
        question.score(),
        question.feedback(),
        question.isFollowUp(),
        question.parentQuestionIndex(),
        question.requirementId()
    );
  }

  private AgentAbilityProfileItem toAgentAbilityProfile(
      com.yzh666.careerai.modules.interview.model.AbilityProfileItemDTO item
  ) {
    return new AgentAbilityProfileItem(
        item.dimension(),
        item.abilityKey(),
        item.displayName(),
        item.score(),
        item.confidence(),
        item.status(),
        item.trend(),
        item.observationCount(),
        item.sessionCount(),
        item.latestSessionId(),
        item.latestQuestionIndex(),
        item.latestEvidence(),
        item.latestMissingPoints(),
        item.lastObservedAt());
  }

  private AgentInterviewSession toInterviewSession(InterviewSessionDTO session) {
    return new AgentInterviewSession(
        session.sessionId(),
        session.resumeText(),
        session.totalQuestions(),
        session.currentQuestionIndex(),
        session.questions().stream().map(this::toInterviewQuestion).toList(),
        session.status().name(),
        toAgentInterviewBlueprint(session.blueprint()),
        session.endReason(),
        session.completionType(),
        session.coveredTargets(),
        session.unverifiedTargets()
    );
  }

  private InterviewBlueprintDTO toInterviewBlueprint(AgentInterviewBlueprint blueprint) {
    if (blueprint == null) {
      return null;
    }
    return new InterviewBlueprintDTO(
        blueprint.mode(),
        blueprint.objective(),
        blueprint.targetRequirementIds(),
        blueprint.focusTopics(),
        blueprint.questionTypes(),
        blueprint.avoidTopics(),
        blueprint.difficulty(),
        blueprint.questionCount(),
        blueprint.maxFollowUpsPerTopic(),
        blueprint.rationale()
    );
  }

  private AgentInterviewBlueprint toAgentInterviewBlueprint(InterviewBlueprintDTO blueprint) {
    if (blueprint == null) {
      return null;
    }
    return new AgentInterviewBlueprint(
        blueprint.mode(),
        blueprint.objective(),
        blueprint.targetRequirementIds(),
        blueprint.focusTopics(),
        blueprint.questionTypes(),
        blueprint.avoidTopics(),
        blueprint.difficulty(),
        blueprint.questionCount(),
        blueprint.maxFollowUpsPerTopic(),
        blueprint.rationale()
    );
  }

  private String enumName(Enum<?> value) {
    return value == null ? null : value.name();
  }
}
