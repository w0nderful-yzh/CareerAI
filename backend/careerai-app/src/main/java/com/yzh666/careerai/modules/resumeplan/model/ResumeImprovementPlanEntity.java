package com.yzh666.careerai.modules.resumeplan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "resume_improvement_plans", indexes = {
    @Index(name = "idx_resume_plan_user_created", columnList = "userId,createdAt"),
    @Index(name = "idx_resume_plan_user_report_created", columnList = "userId,matchReportId,createdAt"),
    @Index(name = "idx_resume_plan_user_job_created", columnList = "userId,jobId,createdAt")
}, uniqueConstraints = {
    @UniqueConstraint(
        name = "uk_resume_plan_agent_idempotency",
        columnNames = {"userId", "agentIdempotencyKey"}
    )
})
@Getter
@Setter
@NoArgsConstructor
public class ResumeImprovementPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long matchReportId;

    @Column(nullable = false)
    private Long resumeId;

    @Column(nullable = false, length = 255)
    private String resumeFilename;

    @Column(nullable = false)
    private Long jobId;

    @Column(nullable = false, length = 160)
    private String jobTitle;

    @Column(nullable = false)
    private Integer readinessScore;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String priorityFixesJson;

    @Column(columnDefinition = "TEXT")
    private String resumeRewriteBulletsJson;

    @Column(columnDefinition = "TEXT")
    private String projectUpgradeTasksJson;

    @Column(columnDefinition = "TEXT")
    private String interviewPracticeTasksJson;

    @Column(columnDefinition = "TEXT")
    private String learningTasksJson;

    /** 仅 Agent 写操作使用，用于避免重试时重复生成计划。 */
    @Column(length = 120)
    private String agentIdempotencyKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
