package com.yzh666.careerai.modules.interview.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.yzh666.careerai.modules.interview.model.AbilityProfileItemDTO;
import com.yzh666.careerai.modules.interview.model.InterviewClosureEntity;
import com.yzh666.careerai.modules.interview.model.InterviewImprovementTaskEntity;
import com.yzh666.careerai.modules.interview.repository.InterviewClosureRepository;
import com.yzh666.careerai.modules.interview.repository.InterviewImprovementTaskRepository;
import com.yzh666.careerai.modules.interview.repository.InterviewSessionRepository;
import com.yzh666.careerai.modules.user.service.CurrentUserService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class InterviewPlanningContextServiceTest {

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
  }

  @Test
  void exposesProfilePendingTasksAndDeduplicatedUnverifiedTargets() {
    when(currentUserService.currentUserId()).thenReturn(7L);
    when(abilityProfileService.getCurrentProfile()).thenReturn(List.of(profile()));
    when(taskRepository.findTop10ByUserIdAndStatusOrderByCreatedAtDesc(7L, "TODO"))
        .thenReturn(List.of(task()));
    when(closureRepository.findTop5ByUserIdOrderByGeneratedAtDesc(7L))
        .thenReturn(List.of(closure("[\"Kubernetes\",\"消息队列\"]"), closure("[\"Kubernetes\"]")));

    var context = service.getPlanningContext();

    assertEquals("Redis 一致性", context.abilityProfile().getFirst().displayName());
    assertEquals("补齐事务边界", context.pendingTasks().getFirst().title());
    assertEquals(List.of("Kubernetes", "消息队列"), context.recentUnverifiedTargets());
  }

  private AbilityProfileItemDTO profile() {
    return new AbilityProfileItemDTO(
        1L, "TECHNICAL", "redis", "Redis 一致性", 62, 75,
        "STABLE", "DECLINING", 3, 2, "session-1", 0,
        List.of("延迟双删"), List.of("事务边界"), LocalDateTime.now());
  }

  private InterviewImprovementTaskEntity task() {
    InterviewImprovementTaskEntity task = new InterviewImprovementTaskEntity();
    task.setId(1L);
    task.setSessionId("session-1");
    task.setQuestionIndex(0);
    task.setCategory("Redis");
    task.setPriority("HIGH");
    task.setStatus("TODO");
    task.setTitle("补齐事务边界");
    task.setRationale("上一场回答缺失");
    task.setVerificationMethod("重新回答并说明时序");
    return task;
  }

  private InterviewClosureEntity closure(String unverifiedTargetsJson) {
    InterviewClosureEntity closure = new InterviewClosureEntity();
    closure.setUnverifiedTargetsJson(unverifiedTargetsJson);
    return closure;
  }
}
