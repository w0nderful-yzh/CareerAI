package com.yzh666.careerai.common.agent.tool;

import java.util.List;

/** 根据已完成的匹配报告和 Agent 决策创建改进计划。 */
public record AgentResumeImprovementPlanCommand(
    Long matchReportId,
    String strategy,
    String rationale,
    List<String> prioritizedGaps,
    List<String> supportingEvidence,
    List<String> interviewFocus
) {
}
