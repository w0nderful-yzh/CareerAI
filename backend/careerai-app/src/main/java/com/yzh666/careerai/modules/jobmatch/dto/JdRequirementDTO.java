package com.yzh666.careerai.modules.jobmatch.dto;

/** 从 JD 原文中拆出的单项岗位要求，sourceQuote 用于保留可追溯依据。 */
public record JdRequirementDTO(
    String id,
    String category,
    String description,
    String importance,
    String sourceQuote
) {
}
