package com.yzh666.careerai.modules.interview.service;

import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnEvaluation;
import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.modules.interview.model.AbilityObservationEntity;
import com.yzh666.careerai.modules.interview.model.AbilityProfileEntity;
import com.yzh666.careerai.modules.interview.model.AbilityProfileItemDTO;
import com.yzh666.careerai.modules.interview.model.InterviewAnswerEntity;
import com.yzh666.careerai.modules.interview.model.InterviewQuestionDTO;
import com.yzh666.careerai.modules.interview.repository.AbilityObservationRepository;
import com.yzh666.careerai.modules.interview.repository.AbilityProfileRepository;
import com.yzh666.careerai.modules.user.service.CurrentUserService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 将单轮多维评价沉淀为不可变观察，并用确定性规则计算长期画像。
 * 同一场面试先聚合成一个样本，避免连续追问把单场表现放大成稳定结论。
 */
@Service
@RequiredArgsConstructor
public class AbilityProfileService {

  private static final String TECHNICAL = "TECHNICAL";
  private static final String PROJECT = "PROJECT";
  private static final String COMMUNICATION = "COMMUNICATION";

  private final AbilityObservationRepository observationRepository;
  private final AbilityProfileRepository profileRepository;
  private final CurrentUserService currentUserService;
  private final ObjectMapper objectMapper;

  @Transactional(rollbackFor = Exception.class)
  public void recordTurn(
      String sessionId,
      InterviewQuestionDTO question,
      InterviewAnswerEntity answer,
      AgentInterviewTurnEvaluation evaluation
  ) {
    if (question == null || answer == null || answer.getId() == null
        || evaluation == null || !evaluation.answered()) {
      return;
    }
    Long userId = currentUserService.currentUserId();
    String displayName = normalizeDisplayName(question.category());
    List<ObservationDraft> drafts = new ArrayList<>();

    addDraft(drafts, TECHNICAL, topicKey(displayName), displayName, evaluation.confidence(),
        evaluation.technicalCorrectness(), evaluation.technicalDepth(), evaluation.completeness(),
        evaluation.scenarioReasoning(), evaluation.troubleshooting(), evaluation.jobRelevance());
    if (evaluation.projectUnderstanding() != null
        || "PROJECT_EVIDENCE".equalsIgnoreCase(question.type())) {
      addDraft(drafts, PROJECT, topicKey(displayName), displayName, evaluation.confidence(),
          evaluation.projectUnderstanding(), evaluation.credibility());
    }
    addDraft(drafts, COMMUNICATION, "expression", "表达与结构", evaluation.confidence(),
        evaluation.expressionStructure(), evaluation.clarity());

    for (ObservationDraft draft : drafts) {
      if (observationRepository.existsByEvidenceIdAndDimensionAndAbilityKey(
          answer.getId(), draft.dimension(), draft.abilityKey())) {
        continue;
      }
      AbilityObservationEntity observation = new AbilityObservationEntity();
      observation.setUserId(userId);
      observation.setSessionId(sessionId);
      observation.setQuestionIndex(answer.getQuestionIndex());
      observation.setDimension(draft.dimension());
      observation.setAbilityKey(draft.abilityKey());
      observation.setDisplayName(draft.displayName());
      observation.setScore(draft.score());
      observation.setConfidence(draft.confidence());
      observation.setSignal(toSignal(draft.score()));
      observation.setEvidenceType("INTERVIEW_ANSWER");
      observation.setEvidenceId(answer.getId());
      observation.setEvidenceJson(writeJson(evaluation.evidenceSnippets()));
      observation.setMissingPointsJson(writeJson(evaluation.missingPoints()));
      observation.setErrorsJson(writeJson(evaluation.errors()));
      observationRepository.save(observation);
      rebuildProjection(userId, draft.dimension(), draft.abilityKey());
    }
  }

  public List<AbilityProfileItemDTO> getCurrentProfile() {
    Long userId = currentUserService.currentUserId();
    return profileRepository.findByUserIdOrderByLastObservedAtDesc(userId).stream()
        .map(profile -> toDto(userId, profile))
        .toList();
  }

  private void addDraft(
      List<ObservationDraft> drafts,
      String dimension,
      String abilityKey,
      String displayName,
      int confidence,
      Integer... scores
  ) {
    List<Integer> observedScores = java.util.Arrays.stream(scores)
        .filter(score -> score != null)
        .toList();
    if (observedScores.isEmpty()) {
      return;
    }
    int score = (int) Math.round(observedScores.stream()
        .mapToInt(Integer::intValue)
        .average()
        .orElse(0));
    drafts.add(new ObservationDraft(
        dimension, abilityKey, displayName, score, clamp(confidence)));
  }

  private void rebuildProjection(Long userId, String dimension, String abilityKey) {
    List<AbilityObservationEntity> observations = observationRepository
        .findByUserIdAndDimensionAndAbilityKeyOrderByObservedAtAsc(userId, dimension, abilityKey);
    if (observations.isEmpty()) {
      return;
    }
    Map<String, List<AbilityObservationEntity>> bySession = new LinkedHashMap<>();
    observations.forEach(observation -> bySession
        .computeIfAbsent(observation.getSessionId(), ignored -> new ArrayList<>())
        .add(observation));
    List<SessionScore> sessionScores = bySession.values().stream()
        .map(this::aggregateSession)
        .toList();
    int score = (int) Math.round(sessionScores.stream()
        .mapToInt(SessionScore::score)
        .average()
        .orElse(0));
    int baseConfidence = (int) Math.round(sessionScores.stream()
        .mapToInt(SessionScore::confidence)
        .average()
        .orElse(0));
    boolean conflict = sessionScores.size() >= 2
        && sessionScores.stream().mapToInt(SessionScore::score).min().orElse(100) < 60
        && sessionScores.stream().mapToInt(SessionScore::score).max().orElse(0) >= 75;
    int confidence = clamp(baseConfidence + Math.min(sessionScores.size() - 1, 3) * 5
        - (conflict ? 15 : 0));
    String status = conflict
        ? "CONFLICT"
        : sessionScores.size() >= 2 && confidence >= 65 ? "STABLE" : "CANDIDATE";
    String trend = calculateTrend(sessionScores);
    AbilityObservationEntity latest = observations.getLast();
    AbilityProfileEntity profile = profileRepository
        .findByUserIdAndDimensionAndAbilityKey(userId, dimension, abilityKey)
        .orElseGet(AbilityProfileEntity::new);
    profile.setUserId(userId);
    profile.setDimension(dimension);
    profile.setAbilityKey(abilityKey);
    profile.setDisplayName(latest.getDisplayName());
    profile.setScore(score);
    profile.setConfidence(confidence);
    profile.setStatus(status);
    profile.setTrend(trend);
    profile.setObservationCount(observations.size());
    profile.setSessionCount(sessionScores.size());
    profile.setLatestObservationId(latest.getId());
    profile.setLastObservedAt(latest.getObservedAt());
    profileRepository.save(profile);
  }

  private SessionScore aggregateSession(List<AbilityObservationEntity> observations) {
    int totalWeight = observations.stream()
        .mapToInt(observation -> Math.max(observation.getConfidence(), 1))
        .sum();
    int score = (int) Math.round(observations.stream()
        .mapToInt(observation -> observation.getScore() * Math.max(observation.getConfidence(), 1))
        .sum() / (double) totalWeight);
    int confidence = (int) Math.round(observations.stream()
        .mapToInt(AbilityObservationEntity::getConfidence)
        .average()
        .orElse(0));
    return new SessionScore(score, confidence);
  }

  private String calculateTrend(List<SessionScore> sessionScores) {
    if (sessionScores.size() < 2) {
      return "UNKNOWN";
    }
    int latest = sessionScores.getLast().score();
    double previous = sessionScores.subList(0, sessionScores.size() - 1).stream()
        .mapToInt(SessionScore::score)
        .average()
        .orElse(latest);
    if (latest - previous >= 8) {
      return "IMPROVING";
    }
    if (latest - previous <= -8) {
      return "DECLINING";
    }
    return "STABLE";
  }

  private AbilityProfileItemDTO toDto(Long userId, AbilityProfileEntity profile) {
    AbilityObservationEntity latest = observationRepository
        .findByIdAndUserId(profile.getLatestObservationId(), userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "能力画像证据不存在"));
    return new AbilityProfileItemDTO(
        profile.getId(),
        profile.getDimension(),
        profile.getAbilityKey(),
        profile.getDisplayName(),
        profile.getScore(),
        profile.getConfidence(),
        profile.getStatus(),
        profile.getTrend(),
        profile.getObservationCount(),
        profile.getSessionCount(),
        latest.getSessionId(),
        latest.getQuestionIndex(),
        readStringList(latest.getEvidenceJson()),
        readStringList(latest.getMissingPointsJson()),
        profile.getLastObservedAt());
  }

  private String normalizeDisplayName(String value) {
    if (value == null || value.isBlank()) {
      return "综合技术能力";
    }
    String normalized = value.trim().replaceFirst("（追问\\d+）$", "").trim();
    return normalized.substring(0, Math.min(normalized.length(), 120));
  }

  private String topicKey(String displayName) {
    String normalized = displayName.toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", "-")
        .replaceAll("[^\\p{L}\\p{N}_-]", "-")
        .replaceAll("-+", "-")
        .replaceAll("^-|-$", "");
    return normalized.isBlank() ? "general" : normalized.substring(0, Math.min(normalized.length(), 160));
  }

  private String toSignal(int score) {
    if (score >= 75) {
      return "POSITIVE";
    }
    if (score < 60) {
      return "NEGATIVE";
    }
    return "MIXED";
  }

  private int clamp(int value) {
    return Math.max(0, Math.min(value, 100));
  }

  private String writeJson(List<String> values) {
    try {
      return objectMapper.writeValueAsString(values == null ? List.of() : values);
    } catch (JacksonException e) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存能力画像证据失败");
    }
  }

  private List<String> readStringList(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (JacksonException e) {
      return List.of();
    }
  }

  private record ObservationDraft(
      String dimension,
      String abilityKey,
      String displayName,
      int score,
      int confidence
  ) {}

  private record SessionScore(int score, int confidence) {}
}
