package com.yzh666.careerai.common.agent.tool;

import java.time.LocalDateTime;

/** 已由 Java 校验并执行的面试决策记录。 */
public record AgentInterviewDecision(
    int questionIndex,
    String action,
    String rationale,
    int answerScore,
    String feedback,
    String difficultyAdjustment,
    Integer targetQuestionIndex,
    String targetRequirementId,
    LocalDateTime createdAt
) {}
