package com.yzh666.careerai.common.agent.tool;

import java.util.List;

/**
 * Agent 对单轮回答的多维观察。维度不适用时使用 null，不能用 0 分代替“未观察”。
 */
public record AgentInterviewTurnEvaluation(
    boolean answered,
    Integer technicalCorrectness,
    Integer technicalDepth,
    Integer completeness,
    Integer scenarioReasoning,
    Integer projectUnderstanding,
    Integer troubleshooting,
    Integer expressionStructure,
    Integer clarity,
    Integer credibility,
    Integer jobRelevance,
    List<String> missingPoints,
    List<String> errors,
    List<String> evidenceSnippets,
    int confidence
) {}
