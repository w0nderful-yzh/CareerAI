package com.yzh666.careerai.modules.interview.model;

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
import lombok.Setter;

/** 一场面试结束后生成的确定性收口产物，内容均可回溯到报告或真实回答。 */
@Getter
@Setter
@Entity
@Table(name = "interview_closures",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_interview_closure_session", columnNames = "session_id"),
        @UniqueConstraint(name = "uk_interview_closure_idempotency", columnNames = "idempotency_key")
    },
    indexes = @Index(name = "idx_interview_closure_user_time", columnList = "user_id,generated_at"))
public class InterviewClosureEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "session_id", nullable = false, length = 36)
  private String sessionId;

  @Column(name = "idempotency_key", nullable = false, length = 160)
  private String idempotencyKey;

  @Column(name = "completion_type", length = 16)
  private String completionType;

  @Column(name = "end_reason", length = 32)
  private String endReason;

  @Column(name = "overall_score")
  private Integer overallScore;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String summary;

  @Column(name = "strengths_json", columnDefinition = "TEXT", nullable = false)
  private String strengthsJson;

  @Column(name = "observed_weaknesses_json", columnDefinition = "TEXT", nullable = false)
  private String observedWeaknessesJson;

  @Column(name = "covered_targets_json", columnDefinition = "TEXT", nullable = false)
  private String coveredTargetsJson;

  @Column(name = "unverified_targets_json", columnDefinition = "TEXT", nullable = false)
  private String unverifiedTargetsJson;

  @Column(name = "key_evidence_json", columnDefinition = "TEXT", nullable = false)
  private String keyEvidenceJson;

  @Column(name = "next_interview_suggestions_json", columnDefinition = "TEXT", nullable = false)
  private String nextInterviewSuggestionsJson;

  @Column(name = "generated_at", nullable = false)
  private LocalDateTime generatedAt;

  @PrePersist
  void onCreate() {
    generatedAt = generatedAt == null ? LocalDateTime.now() : generatedAt;
  }
}
