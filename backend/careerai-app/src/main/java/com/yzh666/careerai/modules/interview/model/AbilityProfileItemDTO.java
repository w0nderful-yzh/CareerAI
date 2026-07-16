package com.yzh666.careerai.modules.interview.model;

import java.time.LocalDateTime;
import java.util.List;

/** 前端和 Agent 读取的可追溯能力画像项。 */
public record AbilityProfileItemDTO(
    Long id,
    String dimension,
    String abilityKey,
    String displayName,
    int score,
    int confidence,
    String status,
    String trend,
    int observationCount,
    int sessionCount,
    String latestSessionId,
    int latestQuestionIndex,
    List<String> latestEvidence,
    List<String> latestMissingPoints,
    LocalDateTime lastObservedAt
) {}
