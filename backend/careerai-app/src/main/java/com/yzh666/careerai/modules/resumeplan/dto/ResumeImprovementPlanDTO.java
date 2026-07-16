package com.yzh666.careerai.modules.resumeplan.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ResumeImprovementPlanDTO(
    Long id,
    Long matchReportId,
    Long resumeId,
    String resumeFilename,
    Long jobId,
    String jobTitle,
    Integer readinessScore,
    String summary,
    List<String> priorityFixes,
    List<String> resumeRewriteBullets,
    List<String> projectUpgradeTasks,
    List<String> interviewPracticeTasks,
    List<String> learningTasks,
    List<PreparationTaskDTO> preparationTasks,
    LocalDateTime createdAt
) {
}
