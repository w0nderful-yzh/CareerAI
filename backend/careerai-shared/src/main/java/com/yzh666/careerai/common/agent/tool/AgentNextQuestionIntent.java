package com.yzh666.careerai.common.agent.tool;

/**
 * Python 只决定下一题的考察意图，不直接生成最终题目文本。
 * Java 会结合真实简历、JD、蓝图和历史问题生成并校验题目。
 */
public record AgentNextQuestionIntent(
    String questionType,
    String topic,
    String requirementId,
    String difficulty,
    boolean followUp,
    Integer parentQuestionIndex,
    String objective
) {}
