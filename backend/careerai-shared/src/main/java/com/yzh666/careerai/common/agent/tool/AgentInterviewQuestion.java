package com.yzh666.careerai.common.agent.tool;

/** Agent 可选择的面试问题快照，不允许模型直接改写业务题库。 */
public record AgentInterviewQuestion(
    int questionIndex,
    String question,
    String type,
    String category,
    String topicSummary,
    String userAnswer,
    Integer score,
    String feedback,
    boolean followUp,
    Integer parentQuestionIndex,
    String requirementId
) {}
