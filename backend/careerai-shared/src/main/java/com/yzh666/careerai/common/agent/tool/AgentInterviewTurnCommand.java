package com.yzh666.careerai.common.agent.tool;

/**
 * Agent 单轮面试决策命令。
 * Python 只提交下一题的受控意图，最终题目由 Java 结合业务上下文生成并校验。
 */
public record AgentInterviewTurnCommand(
    int questionIndex,
    String answer,
    String action,
    String rationale,
    int answerScore,
    String feedback,
    String difficultyAdjustment,
    AgentNextQuestionIntent nextQuestionIntent,
    AgentInterviewTurnEvaluation evaluation,
    String endReason,
    String intent
) {}
