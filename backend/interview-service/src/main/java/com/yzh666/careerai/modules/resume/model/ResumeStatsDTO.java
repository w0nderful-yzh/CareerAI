package com.yzh666.careerai.modules.resume.model;

/**
 * 简历统计 DTO
 */
public record ResumeStatsDTO(
    Integer totalCount,
    Integer totalInterviewCount,
    Integer totalAccessCount
) {}
