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

/** 由已作答证据生成的可执行改进任务；未考察项不会在这里创建任务。 */
@Getter
@Setter
@Entity
@Table(name = "interview_improvement_tasks",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_interview_improvement_task_idempotency", columnNames = "idempotency_key"),
    indexes = {
        @Index(name = "idx_interview_improvement_task_session", columnList = "session_id"),
        @Index(name = "idx_interview_improvement_task_user_status", columnList = "user_id,status,created_at")
    })
public class InterviewImprovementTaskEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "closure_id", nullable = false)
  private Long closureId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "session_id", nullable = false, length = 36)
  private String sessionId;

  @Column(name = "idempotency_key", nullable = false, length = 200)
  private String idempotencyKey;

  @Column(name = "question_index", nullable = false)
  private Integer questionIndex;

  @Column(nullable = false, length = 120)
  private String category;

  @Column(nullable = false, length = 16)
  private String priority;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(nullable = false, length = 300)
  private String title;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String rationale;

  @Column(name = "verification_method", columnDefinition = "TEXT", nullable = false)
  private String verificationMethod;

  @Column(name = "evidence_json", columnDefinition = "TEXT", nullable = false)
  private String evidenceJson;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void onCreate() {
    createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
  }
}
