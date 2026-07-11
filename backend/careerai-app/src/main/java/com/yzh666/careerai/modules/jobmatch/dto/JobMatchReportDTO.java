package com.yzh666.careerai.modules.jobmatch.dto;

import java.time.LocalDateTime;
import java.util.List;

public record JobMatchReportDTO(
    Long id,
    Long resumeId,
    String resumeFilename,
    Long jobId,
    String jobTitle,
    Integer overallScore,
    Integer skillScore,
    Integer projectScore,
    Integer keywordScore,
    String summary,
    List<String> matchedHighlights,
    List<String> gaps,
    List<String> actionItems,
    LocalDateTime createdAt
) {
}
