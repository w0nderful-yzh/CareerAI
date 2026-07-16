package com.yzh666.careerai.common.agent.tool;

import java.time.LocalDateTime;
import java.util.List;

/** Agent 可直接执行和跟踪的结构化简历改进计划。 */
public record AgentResumeImprovementPlan(
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
    LocalDateTime createdAt
) {
}
