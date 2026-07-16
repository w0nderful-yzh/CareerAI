package com.yzh666.careerai.common.agent.tool;

import java.util.List;

/** 单轮面试决策需要的最小业务上下文。 */
public record AgentInterviewTurnContext(
    String sessionId,
    String status,
    String difficulty,
    AgentInterviewQuestion currentQuestion,
    List<AgentInterviewQuestion> askedQuestions,
    AgentInterviewBlueprint blueprint,
    List<AgentAbilityProfileItem> abilityProfile,
    List<AgentRequirementEvidence> evidenceMappings,
    int answeredCount,
    int totalQuestions
) {}
