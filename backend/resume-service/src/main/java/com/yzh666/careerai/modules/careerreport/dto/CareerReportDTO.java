package com.yzh666.careerai.modules.careerreport.dto;

import com.yzh666.careerai.modules.interview.model.InterviewReportDTO;
import java.time.LocalDateTime;
import java.util.List;

public record CareerReportDTO(
    Long matchReportId,
    JobSnapshot job,
    ResumeSnapshot resume,
    MatchSnapshot match,
    InterviewSnapshot latestInterview,
    PlanSnapshot improvementPlan,
    LocalDateTime generatedAt
) {

    public record JobSnapshot(
        Long id,
        String title,
        String company,
        String location,
        String sourceUrl,
        String jdText
    ) {
    }

    public record ResumeSnapshot(
        Long id,
        String filename
    ) {
    }

    public record MatchSnapshot(
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

    public record InterviewSnapshot(
        String sessionId,
        Integer overallScore,
        String overallFeedback,
        InterviewReportDTO.JobEvaluation jobEvaluation,
        LocalDateTime completedAt
    ) {
    }

    public record PlanSnapshot(
        Long id,
        Integer readinessScore,
        String summary,
        List<String> priorityFixes,
        List<String> resumeRewriteBullets,
        List<String> projectUpgradeTasks,
        List<String> interviewPracticeTasks,
        List<String> learningTasks,
        LocalDateTime createdAt
    ) {
    }
}
