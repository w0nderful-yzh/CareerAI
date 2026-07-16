package com.yzh666.careerai.modules.interview.service;

import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnEvaluation;
import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.modules.interview.model.InterviewClosureDTO;
import com.yzh666.careerai.modules.interview.model.InterviewClosureDTO.ImprovementTask;
import com.yzh666.careerai.modules.interview.model.InterviewClosureDTO.KeyEvidence;
import com.yzh666.careerai.modules.interview.model.InterviewClosureEntity;
import com.yzh666.careerai.modules.interview.model.InterviewImprovementTaskEntity;
import com.yzh666.careerai.modules.interview.model.InterviewPlanningContextDTO;
import com.yzh666.careerai.modules.interview.model.InterviewReportDTO;
import com.yzh666.careerai.modules.interview.model.InterviewSessionEntity;
import com.yzh666.careerai.modules.interview.model.TurnEvaluationEvidenceDTO;
import com.yzh666.careerai.modules.interview.repository.InterviewClosureRepository;
import com.yzh666.careerai.modules.interview.repository.InterviewImprovementTaskRepository;
import com.yzh666.careerai.modules.interview.repository.InterviewSessionRepository;
import com.yzh666.careerai.modules.user.service.CurrentUserService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 把最终报告收口为可查询的业务产物。
 * 这里不再调用模型：总结复用报告，弱项与任务只读取已回答轮次的结构化证据。
 */
@Service
@RequiredArgsConstructor
public class InterviewClosureService {

  private static final int MAX_TASKS = 6;
  private static final String CLOSURE_KEY_PREFIX = "interview-closure:";
  private static final String TASK_KEY_PREFIX = "interview-improvement:";

  private final InterviewClosureRepository closureRepository;
  private final InterviewImprovementTaskRepository taskRepository;
  private final InterviewSessionRepository sessionRepository;
  private final InterviewPersistenceService persistenceService;
  private final AbilityProfileService abilityProfileService;
  private final CurrentUserService currentUserService;
  private final ObjectMapper objectMapper;

  /** 异步消息重试时先命中 session 唯一记录，不会重复创建总结或任务。 */
  @Transactional(rollbackFor = Exception.class)
  public InterviewClosureDTO finalizeSession(String sessionId, InterviewReportDTO report) {
    InterviewClosureEntity existing = closureRepository.findBySessionId(sessionId).orElse(null);
    if (existing != null) {
      return toDto(existing);
    }

    InterviewSessionEntity session = sessionRepository.findBySessionId(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
    List<TurnEvaluationEvidenceDTO> turns = persistenceService.getTurnEvaluations(sessionId);
    List<KeyEvidence> keyEvidence = turns.stream().map(this::toKeyEvidence).toList();
    List<TaskDraft> taskDrafts = buildTaskDrafts(sessionId, turns);
    List<String> observedWeaknesses = buildObservedWeaknesses(turns);
    List<String> nextSuggestions = buildNextSuggestions(taskDrafts, report.unverifiedTargets());

    InterviewClosureEntity closure = new InterviewClosureEntity();
    closure.setUserId(session.getUserId());
    closure.setSessionId(sessionId);
    closure.setIdempotencyKey(CLOSURE_KEY_PREFIX + sessionId);
    closure.setCompletionType(report.completionType());
    closure.setEndReason(report.endReason());
    closure.setOverallScore(report.overallScore());
    closure.setSummary(buildSummary(report));
    closure.setStrengthsJson(writeJson(nullToEmpty(report.strengths())));
    closure.setObservedWeaknessesJson(writeJson(observedWeaknesses));
    closure.setCoveredTargetsJson(writeJson(nullToEmpty(report.coveredTargets())));
    closure.setUnverifiedTargetsJson(writeJson(nullToEmpty(report.unverifiedTargets())));
    closure.setKeyEvidenceJson(writeJson(keyEvidence));
    closure.setNextInterviewSuggestionsJson(writeJson(nextSuggestions));
    closure = closureRepository.save(closure);

    for (TaskDraft draft : taskDrafts) {
      if (taskRepository.existsByIdempotencyKey(draft.idempotencyKey())) {
        continue;
      }
      taskRepository.save(toEntity(closure, draft));
    }
    return toDto(closure);
  }

  public InterviewClosureDTO getClosure(String sessionId) {
    Long userId = currentUserService.currentUserId();
    InterviewClosureEntity closure = closureRepository.findBySessionIdAndUserId(sessionId, userId)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.NOT_FOUND, "面试结束总结尚未生成"));
    return toDto(closure);
  }

  /** 给面试规划 Agent 提供跨场次事实，不暴露数据库实体。 */
  public InterviewPlanningContextDTO getPlanningContext() {
    Long userId = currentUserService.currentUserId();
    List<InterviewPlanningContextDTO.ImprovementTask> pendingTasks = taskRepository
        .findTop10ByUserIdAndStatusOrderByCreatedAtDesc(userId, "TODO").stream()
        .map(task -> new InterviewPlanningContextDTO.ImprovementTask(
            task.getId(),
            task.getSessionId(),
            task.getQuestionIndex(),
            task.getCategory(),
            task.getPriority(),
            task.getStatus(),
            task.getTitle(),
            task.getRationale(),
            task.getVerificationMethod()))
        .toList();
    Set<String> unverifiedTargets = new LinkedHashSet<>();
    for (InterviewClosureEntity closure
        : closureRepository.findTop5ByUserIdOrderByGeneratedAtDesc(userId)) {
      unverifiedTargets.addAll(readJson(
          closure.getUnverifiedTargetsJson(), new TypeReference<List<String>>() {}));
    }
    return new InterviewPlanningContextDTO(
        abilityProfileService.getCurrentProfile(),
        pendingTasks,
        unverifiedTargets.stream().limit(10).toList()
    );
  }

  private String buildSummary(InterviewReportDTO report) {
    String prefix = "PARTIAL".equalsIgnoreCase(report.completionType())
        ? "本次为部分评价，仅基于已完成回答。"
        : "本次面试已按计划完成。";
    String feedback = report.overallFeedback() == null ? "" : report.overallFeedback().trim();
    return feedback.isBlank() ? prefix : prefix + feedback;
  }

  private List<String> buildObservedWeaknesses(List<TurnEvaluationEvidenceDTO> turns) {
    Set<String> weaknesses = new LinkedHashSet<>();
    for (TurnEvaluationEvidenceDTO turn : turns) {
      AgentInterviewTurnEvaluation evaluation = turn.evaluation();
      for (String error : nullToEmpty(evaluation.errors())) {
        addWeakness(weaknesses, turn, "存在错误：" + error);
      }
      for (String missing : nullToEmpty(evaluation.missingPoints())) {
        addWeakness(weaknesses, turn, "缺少要点：" + missing);
      }
    }
    return weaknesses.stream().limit(MAX_TASKS).toList();
  }

  private void addWeakness(
      Set<String> weaknesses,
      TurnEvaluationEvidenceDTO turn,
      String description
  ) {
    if (description != null && !description.isBlank()) {
      weaknesses.add("第" + (turn.questionIndex() + 1) + "题（"
          + safeCategory(turn.category()) + "）" + description.trim());
    }
  }

  private List<TaskDraft> buildTaskDrafts(
      String sessionId,
      List<TurnEvaluationEvidenceDTO> turns
  ) {
    List<TaskDraft> drafts = new ArrayList<>();
    for (TurnEvaluationEvidenceDTO turn : turns) {
      AgentInterviewTurnEvaluation evaluation = turn.evaluation();
      int ordinal = 0;
      for (String error : nullToEmpty(evaluation.errors())) {
        addTask(drafts, sessionId, turn, "ERROR", ordinal++, error, true);
      }
      ordinal = 0;
      for (String missing : nullToEmpty(evaluation.missingPoints())) {
        addTask(drafts, sessionId, turn, "MISSING", ordinal++, missing, false);
      }
      if (drafts.size() >= MAX_TASKS) {
        break;
      }
    }
    return drafts.stream().limit(MAX_TASKS).toList();
  }

  private void addTask(
      List<TaskDraft> drafts,
      String sessionId,
      TurnEvaluationEvidenceDTO turn,
      String kind,
      int ordinal,
      String problem,
      boolean error
  ) {
    if (problem == null || problem.isBlank() || drafts.size() >= MAX_TASKS) {
      return;
    }
    String category = safeCategory(turn.category());
    String action = error ? "纠正" : "补齐";
    String idempotencyKey = TASK_KEY_PREFIX + sessionId + ":q" + turn.questionIndex()
        + ":" + kind.toLowerCase() + ":" + ordinal;
    int score = observedScore(turn.evaluation());
    String rationale = "来源于第" + (turn.questionIndex() + 1) + "题的实际回答："
        + problem.trim();
    String verification = "重新回答第" + (turn.questionIndex() + 1)
        + "题，明确给出结论、依据、权衡和边界条件，并覆盖“" + problem.trim() + "”。";
    drafts.add(new TaskDraft(
        idempotencyKey,
        turn.questionIndex(),
        category,
        error || score < 60 ? "HIGH" : "MEDIUM",
        truncate(action + "「" + problem.trim() + "」并完成一次复述验证", 300),
        rationale,
        verification,
        nullToEmpty(turn.evaluation().evidenceSnippets())
    ));
  }

  private List<String> buildNextSuggestions(
      List<TaskDraft> tasks,
      List<String> unverifiedTargets
  ) {
    List<String> suggestions = new ArrayList<>();
    tasks.stream().limit(3)
        .map(task -> "优先复测：" + task.title())
        .forEach(suggestions::add);
    nullToEmpty(unverifiedTargets).stream().limit(3)
        .map(target -> "待验证（非弱项）：" + target)
        .forEach(suggestions::add);
    if (suggestions.isEmpty()) {
      suggestions.add("下一场提升问题难度，验证当前优势在复杂场景下是否稳定。");
    }
    return suggestions;
  }

  private KeyEvidence toKeyEvidence(TurnEvaluationEvidenceDTO turn) {
    return new KeyEvidence(
        turn.questionIndex(),
        turn.question(),
        safeCategory(turn.category()),
        observedScore(turn.evaluation()),
        nullToEmpty(turn.evaluation().evidenceSnippets()),
        nullToEmpty(turn.evaluation().missingPoints()),
        nullToEmpty(turn.evaluation().errors())
    );
  }

  private int observedScore(AgentInterviewTurnEvaluation evaluation) {
    List<Integer> scores = java.util.stream.Stream.of(
            evaluation.technicalCorrectness(), evaluation.technicalDepth(),
            evaluation.completeness(), evaluation.scenarioReasoning(),
            evaluation.projectUnderstanding(), evaluation.troubleshooting(),
            evaluation.expressionStructure(), evaluation.clarity(),
            evaluation.credibility(), evaluation.jobRelevance())
        .filter(java.util.Objects::nonNull)
        .toList();
    return scores.isEmpty() ? 0 : (int) Math.round(scores.stream()
        .mapToInt(Integer::intValue).average().orElse(0));
  }

  private InterviewImprovementTaskEntity toEntity(
      InterviewClosureEntity closure,
      TaskDraft draft
  ) {
    InterviewImprovementTaskEntity task = new InterviewImprovementTaskEntity();
    task.setClosureId(closure.getId());
    task.setUserId(closure.getUserId());
    task.setSessionId(closure.getSessionId());
    task.setIdempotencyKey(draft.idempotencyKey());
    task.setQuestionIndex(draft.questionIndex());
    task.setCategory(draft.category());
    task.setPriority(draft.priority());
    task.setStatus("TODO");
    task.setTitle(draft.title());
    task.setRationale(draft.rationale());
    task.setVerificationMethod(draft.verificationMethod());
    task.setEvidenceJson(writeJson(draft.evidenceSnippets()));
    return task;
  }

  private InterviewClosureDTO toDto(InterviewClosureEntity closure) {
    List<ImprovementTask> tasks = taskRepository
        .findBySessionIdOrderByIdAsc(closure.getSessionId()).stream()
        .map(task -> new ImprovementTask(
            task.getId(),
            task.getIdempotencyKey(),
            task.getQuestionIndex(),
            task.getCategory(),
            task.getPriority(),
            task.getStatus(),
            task.getTitle(),
            task.getRationale(),
            task.getVerificationMethod(),
            readJson(task.getEvidenceJson(), new TypeReference<>() {})))
        .toList();
    return new InterviewClosureDTO(
        closure.getSessionId(),
        closure.getCompletionType(),
        closure.getEndReason(),
        closure.getOverallScore(),
        closure.getSummary(),
        readJson(closure.getStrengthsJson(), new TypeReference<>() {}),
        readJson(closure.getObservedWeaknessesJson(), new TypeReference<>() {}),
        readJson(closure.getCoveredTargetsJson(), new TypeReference<>() {}),
        readJson(closure.getUnverifiedTargetsJson(), new TypeReference<>() {}),
        readJson(closure.getKeyEvidenceJson(), new TypeReference<>() {}),
        readJson(closure.getNextInterviewSuggestionsJson(), new TypeReference<>() {}),
        tasks,
        closure.getGeneratedAt()
    );
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "面试结束产物序列化失败");
    }
  }

  private <T> T readJson(String json, TypeReference<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JacksonException exception) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "面试结束产物读取失败");
    }
  }

  private String safeCategory(String category) {
    return category == null || category.isBlank() ? "综合能力" : truncate(category.trim(), 120);
  }

  private String truncate(String value, int maxLength) {
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  private <T> List<T> nullToEmpty(List<T> values) {
    return values == null ? List.of() : values;
  }

  private record TaskDraft(
      String idempotencyKey,
      int questionIndex,
      String category,
      String priority,
      String title,
      String rationale,
      String verificationMethod,
      List<String> evidenceSnippets
  ) {}
}
