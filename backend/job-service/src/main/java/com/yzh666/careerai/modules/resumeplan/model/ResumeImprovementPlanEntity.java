package com.yzh666.careerai.modules.resumeplan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "resume_improvement_plans", indexes = {
    @Index(name = "idx_resume_plan_user_created", columnList = "userId,createdAt"),
    @Index(name = "idx_resume_plan_user_report_created", columnList = "userId,matchReportId,createdAt"),
    @Index(name = "idx_resume_plan_user_job_created", columnList = "userId,jobId,createdAt")
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

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
