package com.yzh666.careerai.modules.interview.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnEvaluation;
import com.yzh666.careerai.modules.interview.model.InterviewClosureEntity;
import com.yzh666.careerai.modules.interview.model.InterviewImprovementTaskEntity;
import com.yzh666.careerai.modules.interview.model.InterviewReportDTO;
import com.yzh666.careerai.modules.interview.model.InterviewSessionEntity;
import com.yzh666.careerai.modules.interview.model.TurnEvaluationEvidenceDTO;
import com.yzh666.careerai.modules.interview.repository.InterviewClosureRepository;
import com.yzh666.careerai.modules.interview.repository.InterviewImprovementTaskRepository;
import com.yzh666.careerai.modules.interview.repository.InterviewSessionRepository;
import com.yzh666.careerai.modules.user.service.CurrentUserService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class InterviewClosureServiceTest {

  @Mock
  private InterviewClosureRepository closureRepository;
  @Mock
  private InterviewImprovementTaskRepository taskRepository;
  @Mock
  private InterviewSessionRepository sessionRepository;
  @Mock
  private InterviewPersistenceService persistenceService;
  @Mock
  private AbilityProfileService abilityProfileService;
  @Mock
  private CurrentUserService currentUserService;

  private final AtomicReference<InterviewClosureEntity> storedClosure = new AtomicReference<>();
  private final List<InterviewImprovementTaskEntity> storedTasks = new ArrayList<>();
  private InterviewClosureService service;

  @BeforeEach
  void setUp() {
    service = new InterviewClosureService(
        closureRepository,
        taskRepository,
        sessionRepository,
        persistenceService,
        abilityProfileService,
        currentUserService,
        new ObjectMapper());
    when(closureRepository.findBySessionId("session-1"))
        .thenAnswer(ignored -> java.util.Optional.ofNullable(storedClosure.get()));
    when(closureRepository.save(any())).thenAnswer(invocation -> {
      InterviewClosureEntity closure = invocation.getArgument(0);
      closure.setId(1L);
      closure.setGeneratedAt(LocalDateTime.of(2026, 7, 16, 12, 0));
      storedClosure.set(closure);
      return closure;
    });
    when(taskRepository.existsByIdempotencyKey(any())).thenAnswer(invocation -> storedTasks.stream()
        .anyMatch(task -> task.getIdempotencyKey().equals(invocation.getArgument(0))));
    when(taskRepository.save(any())).thenAnswer(invocation -> {
      InterviewImprovementTaskEntity task = invocation.getArgument(0);
      task.setId((long) storedTasks.size() + 1);
      storedTasks.add(task);
      return task;
    });
    when(taskRepository.findBySessionIdOrderByIdAsc("session-1"))
        .thenAnswer(ignored -> List.copyOf(storedTasks));
  }

  @Test
  void partialInterviewCreatesTasksOnlyFromAnsweredEvidenceAndIsIdempotent() {
    InterviewSessionEntity session = new InterviewSessionEntity();
    session.setUserId(7L);
    session.setSessionId("session-1");
    when(sessionRepository.findBySessionId("session-1"))
        .thenReturn(java.util.Optional.of(session));
    when(persistenceService.getTurnEvaluations("session-1"))
        .thenReturn(List.of(answeredTurn()));

    var first = service.finalizeSession("session-1", partialReport());
    var second = service.finalizeSession("session-1", partialReport());

    assertEquals("PARTIAL", first.completionType());
    assertTrue(first.summary().startsWith("本次为部分评价"));
    assertEquals(2, first.improvementTasks().size());
    assertEquals(first.improvementTasks(), second.improvementTasks());
    assertTrue(first.observedWeaknesses().stream().anyMatch(item -> item.contains("事务边界")));
    assertFalse(first.observedWeaknesses().stream().anyMatch(item -> item.contains("Kubernetes")));
    assertTrue(first.nextInterviewSuggestions().contains("待验证（非弱项）：Kubernetes"));
    assertFalse(first.improvementTasks().stream().anyMatch(task -> task.title().contains("Kubernetes")));
    verify(closureRepository, times(1)).save(any());
    verify(taskRepository, times(2)).save(any());
  }

  private TurnEvaluationEvidenceDTO answeredTurn() {
    return new TurnEvaluationEvidenceDTO(
        0,
        "如何保证缓存与数据库一致性？",
        "Redis 一致性",
        "思路基本正确，但事务边界不清晰。",
        new AgentInterviewTurnEvaluation(
            true,
            58,
            62,
            55,
            60,
            null,
            57,
            70,
            72,
            null,
            65,
            List.of("事务边界"),
            List.of("把删除缓存放在事务提交之前"),
            List.of("回答提到延迟双删"),
            82));
  }

  private InterviewReportDTO partialReport() {
    return new InterviewReportDTO(
        "session-1",
        1,
        61,
        List.of(),
        List.of(),
        "已完成部分 Redis 场景考察。",
        List.of("能描述常见一致性方案"),
        List.of("补充事务边界"),
        null,
        List.of(),
        "USER_REQUESTED",
        "PARTIAL",
        List.of("Redis 一致性"),
        List.of("Kubernetes"),
        List.of(),
        List.of());
  }
}
