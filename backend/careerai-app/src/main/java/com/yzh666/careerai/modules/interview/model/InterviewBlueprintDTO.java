package com.yzh666.careerai.modules.interview.model;

import java.util.List;

/** 经 Java 规范化后真正驱动问题生成的面试蓝图。 */
public record InterviewBlueprintDTO(
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
