package com.yzh666.careerai.modules.aitask.model;

import com.yzh666.careerai.common.model.AsyncTaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_analysis_tasks", indexes = {
    @Index(name = "idx_ai_task_user_created", columnList = "userId,createdAt"),
    @Index(name = "idx_ai_task_user_type_status", columnList = "userId,taskType,status"),
    @Index(name = "idx_ai_task_biz", columnList = "taskType,bizId")
}, uniqueConstraints = {
    @UniqueConstraint(
        name = "uk_ai_task_agent_idempotency",
        columnNames = {"userId", "taskType", "agentIdempotencyKey"}
    )
})
@Getter
@Setter
@NoArgsConstructor
public class AiAnalysisTaskEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private AiAnalysisTaskType taskType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AsyncTaskStatus status = AsyncTaskStatus.PENDING;

  @Column(nullable = false)
  private Long bizId;

  private Long resumeId;

  private Long jobId;

  private Long resultId;

  @Column(nullable = false)
  private Integer retryCount = 0;

  @Column(length = 1000)
  private String errorMessage;

  /** 仅 Agent 写操作使用；普通页面请求保持为空。 */
  @Column(length = 120)
  private String agentIdempotencyKey;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
