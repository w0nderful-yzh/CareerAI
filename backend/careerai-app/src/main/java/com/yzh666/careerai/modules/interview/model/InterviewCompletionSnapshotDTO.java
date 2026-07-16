package com.yzh666.careerai.modules.interview.model;

import java.util.List;

/** 面试结束后的覆盖范围快照。 */
public record InterviewCompletionSnapshotDTO(
    String endReason,
    String completionType,
    List<String> coveredTargets,
    List<String> unverifiedTargets
) {}
