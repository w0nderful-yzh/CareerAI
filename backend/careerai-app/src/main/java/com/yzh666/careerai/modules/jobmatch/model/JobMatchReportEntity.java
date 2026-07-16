package com.yzh666.careerai.modules.jobmatch.model;

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
@Table(name = "job_match_reports", indexes = {
    @Index(name = "idx_job_match_user_created", columnList = "userId,createdAt"),
    @Index(name = "idx_job_match_user_job_created", columnList = "userId,jobId,createdAt"),
    @Index(name = "idx_job_match_user_resume_created", columnList = "userId,resumeId,createdAt")
})
@Getter
@Setter
@NoArgsConstructor
public class JobMatchReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long resumeId;

    @Column(nullable = false)
    private Long jobId;

    @Column(nullable = false, length = 255)
    private String resumeFilename;

    @Column(nullable = false, length = 160)
    private String jobTitle;

    @Column(nullable = false)
    private Integer overallScore;

    @Column(nullable = false)
    private Integer skillScore;

    @Column(nullable = false)
    private Integer projectScore;

    @Column(nullable = false)
    private Integer keywordScore;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String matchedHighlightsJson;

    @Column(columnDefinition = "TEXT")
    private String gapsJson;

    @Column(columnDefinition = "TEXT")
    private String actionItemsJson;

    /** 结构化保存 JD 要求、简历证据和覆盖判断，避免只留下不可追溯的总结。 */
    @Column(columnDefinition = "TEXT")
    private String evidenceMappingsJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
