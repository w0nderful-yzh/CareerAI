package com.yzh666.careerai.common.agent.tool;

/** 下一场面试可复测的未完成改进任务。 */
public record AgentInterviewImprovementTask(
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
