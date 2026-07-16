package com.yzh666.careerai.common.agent.tool;

import java.time.LocalDateTime;
import java.util.List;

/** Python Agent 可读取的、带最新问答证据的长期能力画像项。 */
public record AgentAbilityProfileItem(
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
