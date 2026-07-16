package com.yzh666.careerai.common.agent.tool;

/** 暴露给 Python Agent 的简历原文证据。 */
public record AgentResumeEvidence(
    String sourceType,
    String sourceLocation,
    String quote,
    String strength
) {
}
