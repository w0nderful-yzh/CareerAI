package com.yzh666.careerai.modules.interview.model;

import java.util.List;

/** 创建下一场面试时使用的跨场次业务事实。 */
public record InterviewPlanningContextDTO(
    List<AbilityProfileItemDTO> abilityProfile,
    List<ImprovementTask> pendingTasks,
    List<String> recentUnverifiedTargets
) {
  public record ImprovementTask(
      Long id,
      String sessionId,
      int questionIndex,
      String category,
      String priority,
      String status,
      String title,
      String rationale,
      String verificationMethod
  ) {}
}
