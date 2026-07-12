package com.yzh666.careerai.modules.resume.model;

import com.yzh666.careerai.common.model.AsyncTaskStatus;

import java.time.LocalDateTime;

/**
 * 简历列表项DTO
 */
public record ResumeListItemDTO(
    Long id,
    String filename,
    Long fileSize,
    LocalDateTime uploadedAt,
    Integer accessCount,
    Integer latestScore,
    LocalDateTime lastAnalyzedAt,
    Integer interviewCount,
    AsyncTaskStatus analyzeStatus,
    String analyzeError
) {}

