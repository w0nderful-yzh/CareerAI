package com.yzh666.careerai.agentservice.service;

/** 一次 Agent Tool 调用需要透传给核心业务的上下文。 */
public record AgentToolCallContext(
    String authorization,
    String runId,
    String stepId
) {
}
