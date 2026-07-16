package com.yzh666.careerai.modules.interview.model;

import java.time.LocalDateTime;
import java.util.List;

/** 面试结束后的可追溯总结、下一场建议和可执行改进任务。 */
public record InterviewClosureDTO(
    String sessionId,
    String completionType,
    String endReason,
    Integer overallScore,
    String summary,
    List<String> strengths,
    List<String> observedWeaknesses,
    List<String> coveredTargets,
    List<String> unverifiedTargets,
    List<KeyEvidence> keyEvidence,
    List<String> nextInterviewSuggestions,
    List<ImprovementTask> improvementTasks,
    LocalDateTime generatedAt
) {
  public record KeyEvidence(
      int questionIndex,
      String question,
      String category,
      int observedScore,
      List<String> evidenceSnippets,
      List<String> missingPoints,
      List<String> errors
  ) {}

  public record ImprovementTask(
      Long id,
      String idempotencyKey,
      int questionIndex,
      String category,
      String priority,
      String status,
      String title,
      String rationale,
      String verificationMethod,
      List<String> evidenceSnippets
  ) {}
}
