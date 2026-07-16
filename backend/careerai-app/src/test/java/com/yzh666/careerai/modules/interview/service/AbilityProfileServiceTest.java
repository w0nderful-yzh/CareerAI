package com.yzh666.careerai.modules.interview.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnEvaluation;
import com.yzh666.careerai.modules.interview.model.AbilityObservationEntity;
import com.yzh666.careerai.modules.interview.model.AbilityProfileEntity;
import com.yzh666.careerai.modules.interview.model.InterviewAnswerEntity;
import com.yzh666.careerai.modules.interview.model.InterviewQuestionDTO;
import com.yzh666.careerai.modules.interview.repository.AbilityObservationRepository;
import com.yzh666.careerai.modules.interview.repository.AbilityProfileRepository;
import com.yzh666.careerai.modules.user.service.CurrentUserService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AbilityProfileServiceTest {

  @Mock
  private AbilityObservationRepository observationRepository;
  @Mock
  private AbilityProfileRepository profileRepository;
  @Mock
  private CurrentUserService currentUserService;

  private final List<AbilityObservationEntity> observations = new ArrayList<>();
  private final Map<String, AbilityProfileEntity> profiles = new HashMap<>();
  private AbilityProfileService service;

  @BeforeEach
  void setUp() {
    service = new AbilityProfileService(
        observationRepository, profileRepository, currentUserService, new ObjectMapper());
    when(currentUserService.currentUserId()).thenReturn(7L);
    when(observationRepository.existsByEvidenceIdAndDimensionAndAbilityKey(any(), any(), any()))
        .thenAnswer(invocation -> observations.stream().anyMatch(item ->
            item.getEvidenceId().equals(invocation.getArgument(0))
                && item.getDimension().equals(invocation.getArgument(1))
                && item.getAbilityKey().equals(invocation.getArgument(2))));
    when(observationRepository.save(any())).thenAnswer(invocation -> {
      AbilityObservationEntity item = invocation.getArgument(0);
      item.setId((long) observations.size() + 1);
      item.setObservedAt(LocalDateTime.of(2026, 7, 16, 10, 0)
          .plusMinutes(observations.size()));
      observations.add(item);
      return item;
    });
    when(observationRepository
        .findByUserIdAndDimensionAndAbilityKeyOrderByObservedAtAsc(any(), any(), any()))
        .thenAnswer(invocation -> observations.stream()
            .filter(item -> item.getUserId().equals(invocation.getArgument(0)))
            .filter(item -> item.getDimension().equals(invocation.getArgument(1)))
            .filter(item -> item.getAbilityKey().equals(invocation.getArgument(2)))
            .toList());
    when(profileRepository.findByUserIdAndDimensionAndAbilityKey(any(), any(), any()))
        .thenAnswer(invocation -> java.util.Optional.ofNullable(profiles.get(
            invocation.getArgument(1) + ":" + invocation.getArgument(2))));
    when(profileRepository.save(any())).thenAnswer(invocation -> {
      AbilityProfileEntity profile = invocation.getArgument(0);
      profiles.put(profile.getDimension() + ":" + profile.getAbilityKey(), profile);
      return profile;
    });
  }

  @Test
  void oneSessionCannotBecomeStableEvenWithRepeatedFollowUps() {
    InterviewQuestionDTO question = question();

    service.recordTurn("session-1", question, answer(101L, 0), evaluation(82));
    service.recordTurn("session-1", question, answer(102L, 1), evaluation(88));

    AbilityProfileEntity technical = profiles.get("TECHNICAL:redis-一致性");
    assertEquals("CANDIDATE", technical.getStatus());
    assertEquals(2, technical.getObservationCount());
    assertEquals(1, technical.getSessionCount());
    assertEquals(3, profiles.size());
  }

  @Test
  void crossSessionContradictionBecomesConflictInsteadOfOverwritingHistory() {
    InterviewQuestionDTO question = question();
    service.recordTurn("session-1", question, answer(201L, 0), evaluation(86));

    service.recordTurn("session-2", question, answer(202L, 0), evaluation(42));

    AbilityProfileEntity technical = profiles.get("TECHNICAL:redis-一致性");
    assertEquals("CONFLICT", technical.getStatus());
    assertEquals(2, technical.getSessionCount());
    assertEquals("DECLINING", technical.getTrend());
    assertTrue(technical.getScore() > 42 && technical.getScore() < 86);
    assertTrue(technical.getConfidence() < 85);
  }

  private InterviewQuestionDTO question() {
    return InterviewQuestionDTO.create(
        0,
        "缓存与数据库如何保证最终一致性？",
        "PROJECT_EVIDENCE",
        "Redis 一致性",
        "Redis 一致性",
        false,
        null,
        "REQ-1");
  }

  private InterviewAnswerEntity answer(Long id, int questionIndex) {
    InterviewAnswerEntity answer = new InterviewAnswerEntity();
    answer.setId(id);
    answer.setQuestionIndex(questionIndex);
    return answer;
  }

  private AgentInterviewTurnEvaluation evaluation(int score) {
    return new AgentInterviewTurnEvaluation(
        true,
        score,
        score,
        score,
        score,
        score,
        score,
        score,
        score,
        score,
        score,
        List.of("缺少量化指标"),
        List.of(),
        List.of("使用延迟双删"),
        85);
  }
}
