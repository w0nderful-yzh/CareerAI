package com.yzh666.careerai.common.agent.tool;

import java.util.List;

/** 创建完成后的面试会话快照。 */
public record AgentInterviewSession(
    String sessionId,
    String resumeText,
    int totalQuestions,
    int currentQuestionIndex,
    List<AgentInterviewQuestion> questions,
    String status,
    AgentInterviewBlueprint blueprint,
    String endReason,
    String completionType,
    List<String> coveredTargets,
    List<String> unverifiedTargets
) {}
