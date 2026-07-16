package com.yzh666.careerai.modules.interview.model;

import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnEvaluation;

/** 最终报告聚合单轮多维评价时使用的只读证据。 */
public record TurnEvaluationEvidenceDTO(
    int questionIndex,
    String question,
    String category,
    String feedback,
    AgentInterviewTurnEvaluation evaluation
) {}
