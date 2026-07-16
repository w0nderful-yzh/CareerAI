package com.yzh666.careerai.common.agent.tool;

/** 暴露给 Python Agent 的岗位要求与 JD 原文依据。 */
public record AgentJdRequirement(
    String id,
    String category,
    String description,
    String importance,
    String sourceQuote
) {
}
