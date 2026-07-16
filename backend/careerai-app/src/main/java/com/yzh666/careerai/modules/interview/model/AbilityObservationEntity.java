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

/** 单轮回答产生的不可变能力观察；稳定画像只由这些事实重新投影。 */
@Entity
@Table(name = "ability_observations",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_ability_observation_evidence_dimension_key",
        columnNames = {"evidence_id", "dimension", "ability_key"}),
    indexes = {
        @Index(name = "idx_ability_observation_user_key_time",
            columnList = "user_id,dimension,ability_key,observed_at"),
        @Index(name = "idx_ability_observation_session", columnList = "session_id")
    })
public class AbilityObservationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "session_id", nullable = false, length = 36)
  private String sessionId;

  @Column(name = "question_index", nullable = false)
  private Integer questionIndex;

  @Column(name = "dimension", nullable = false, length = 24)
  private String dimension;

  @Column(name = "ability_key", nullable = false, length = 160)
  private String abilityKey;

  @Column(name = "display_name", nullable = false, length = 120)
  private String displayName;

  @Column(nullable = false)
  private Integer score;

  @Column(nullable = false)
  private Integer confidence;

  @Column(nullable = false, length = 16)
  private String signal;

  @Column(name = "evidence_type", nullable = false, length = 32)
  private String evidenceType;

  @Column(name = "evidence_id", nullable = false)
  private Long evidenceId;

  @Column(name = "evidence_json", columnDefinition = "TEXT", nullable = false)
  private String evidenceJson;

  @Column(name = "missing_points_json", columnDefinition = "TEXT")
  private String missingPointsJson;

  @Column(name = "errors_json", columnDefinition = "TEXT")
  private String errorsJson;

  @Column(name = "observed_at", nullable = false)
  private LocalDateTime observedAt;

  @PrePersist
  void onCreate() {
    observedAt = observedAt == null ? LocalDateTime.now() : observedAt;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public Integer getQuestionIndex() {
    return questionIndex;
  }

  public void setQuestionIndex(Integer questionIndex) {
    this.questionIndex = questionIndex;
  }

  public String getDimension() {
    return dimension;
  }

  public void setDimension(String dimension) {
    this.dimension = dimension;
  }

  public String getAbilityKey() {
    return abilityKey;
  }

  public void setAbilityKey(String abilityKey) {
    this.abilityKey = abilityKey;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public Integer getScore() {
    return score;
  }

  public void setScore(Integer score) {
    this.score = score;
  }

  public Integer getConfidence() {
    return confidence;
  }

  public void setConfidence(Integer confidence) {
    this.confidence = confidence;
  }

  public String getSignal() {
    return signal;
  }

  public void setSignal(String signal) {
    this.signal = signal;
  }

  public String getEvidenceType() {
    return evidenceType;
  }

  public void setEvidenceType(String evidenceType) {
    this.evidenceType = evidenceType;
  }

  public Long getEvidenceId() {
    return evidenceId;
  }

  public void setEvidenceId(Long evidenceId) {
    this.evidenceId = evidenceId;
  }

  public String getEvidenceJson() {
    return evidenceJson;
  }

  public void setEvidenceJson(String evidenceJson) {
    this.evidenceJson = evidenceJson;
  }

  public String getMissingPointsJson() {
    return missingPointsJson;
  }

  public void setMissingPointsJson(String missingPointsJson) {
    this.missingPointsJson = missingPointsJson;
  }

  public String getErrorsJson() {
    return errorsJson;
  }

  public void setErrorsJson(String errorsJson) {
    this.errorsJson = errorsJson;
  }

  public LocalDateTime getObservedAt() {
    return observedAt;
  }

  public void setObservedAt(LocalDateTime observedAt) {
    this.observedAt = observedAt;
  }
}
