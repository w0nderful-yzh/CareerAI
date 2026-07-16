package com.yzh666.careerai.common.agent.tool;

import java.util.List;

/** Agent 决策使用的单项 JD 要求覆盖结论。 */
public record AgentRequirementEvidence(
    AgentJdRequirement requirement,
    List<AgentResumeEvidence> resumeEvidence,
    String coverageType,
    Integer confidence,
    String reasoning,
    String recommendedAction
) {
}
