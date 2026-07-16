package com.yzh666.careerai.common.agent.tool;

/** 自定义 JD 面试分类的跨服务契约。 */
public record AgentInterviewCategory(
    String key,
    String label,
    String priority,
    String ref,
    Boolean shared
) {}
