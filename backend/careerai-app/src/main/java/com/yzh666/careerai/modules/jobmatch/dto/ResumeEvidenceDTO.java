package com.yzh666.careerai.modules.jobmatch.dto;

/** 简历中用于支撑某项 JD 要求的原文证据。 */
public record ResumeEvidenceDTO(
    String sourceType,
    String sourceLocation,
    String quote,
    String strength
) {
}
