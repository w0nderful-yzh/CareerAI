package com.yzh666.careerai.common.agent.tool;

import java.util.List;

/** Agent 生成、Java 校验后用于出题的结构化面试蓝图。 */
public record AgentInterviewBlueprint(
    String mode,
    String objective,
    List<String> targetRequirementIds,
    List<String> focusTopics,
    List<String> questionTypes,
    List<String> avoidTopics,
    String difficulty,
    int questionCount,
    int maxFollowUpsPerTopic,
    String rationale
) {}
