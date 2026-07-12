package com.yzh666.careerai.modules.jobmatch.dto;

import com.yzh666.careerai.common.model.AsyncTaskStatus;
import java.time.LocalDateTime;

public record JobMatchTaskDTO(
    Long id,
    AsyncTaskStatus status,
    Long resumeId,
    Long jobId,
    Long reportId,
    Integer retryCount,
    String errorMessage,
    JobMatchReportDTO report,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
