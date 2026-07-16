package com.yzh666.careerai.common.agent.tool;

/** Java 执行 Agent 决策后的业务结果。 */
public record AgentInterviewTurnResult(
    String sessionId,
    boolean completed,
    AgentInterviewQuestion nextQuestion,
    AgentInterviewDecision decision,
    int answeredCount,
    int totalQuestions
) {}
