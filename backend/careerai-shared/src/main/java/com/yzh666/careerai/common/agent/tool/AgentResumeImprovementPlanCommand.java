package com.yzh666.careerai.common.agent.tool;

/** 根据已完成的匹配报告创建改进计划。 */
public record AgentResumeImprovementPlanCommand(
    Long matchReportId
) {
}
