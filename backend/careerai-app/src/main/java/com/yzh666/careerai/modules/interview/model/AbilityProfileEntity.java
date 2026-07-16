package com.yzh666.careerai.modules.interview.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/** 由不可变观察计算出的当前能力画像投影。 */
@Entity
@Table(name = "ability_profiles",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_ability_profile_user_dimension_key",
        columnNames = {"user_id", "dimension", "ability_key"}),
    indexes = @Index(name = "idx_ability_profile_user_status",
        columnList = "user_id,status,last_observed_at"))
public class AbilityProfileEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 24)
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
  private String status;

  @Column(nullable = false, length = 16)
  private String trend;

  @Column(name = "observation_count", nullable = false)
  private Integer observationCount;

  @Column(name = "session_count", nullable = false)
  private Integer sessionCount;

  @Column(name = "latest_observation_id", nullable = false)
  private Long latestObservationId;

  @Column(name = "last_observed_at", nullable = false)
  private LocalDateTime lastObservedAt;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getTrend() {
    return trend;
  }

  public void setTrend(String trend) {
    this.trend = trend;
  }

  public Integer getObservationCount() {
    return observationCount;
  }

  public void setObservationCount(Integer observationCount) {
    this.observationCount = observationCount;
  }

  public Integer getSessionCount() {
    return sessionCount;
  }

  public void setSessionCount(Integer sessionCount) {
    this.sessionCount = sessionCount;
  }

  public Long getLatestObservationId() {
    return latestObservationId;
  }

  public void setLatestObservationId(Long latestObservationId) {
    this.latestObservationId = latestObservationId;
  }

  public LocalDateTime getLastObservedAt() {
    return lastObservedAt;
  }

  public void setLastObservedAt(LocalDateTime lastObservedAt) {
    this.lastObservedAt = lastObservedAt;
  }
}
