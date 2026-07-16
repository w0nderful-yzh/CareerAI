package com.yzh666.careerai.modules.interview.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/** 规范化问题记录，为后续增量出题保留稳定问题 ID 和来源信息。 */
@Entity
@Table(name = "interview_questions",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_interview_question_session_index",
        columnNames = {"session_id", "question_index"}),
    indexes = @Index(
        name = "idx_interview_question_session_index",
        columnList = "session_id,question_index"))
public class InterviewQuestionRecordEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_id", nullable = false)
  private InterviewSessionEntity session;

  @Column(name = "question_index", nullable = false)
  private Integer questionIndex;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String question;

  @Column(length = 64)
  private String skillKey;

  @Column(length = 80)
  private String category;

  @Column(length = 32)
  private String stage;

  @Column(length = 16)
  private String difficulty;

  @Column(length = 32)
  private String sourceType;

  @Column(length = 64)
  private String sourceRef;

  @Column(length = 40)
  private String requirementId;

  private Integer parentQuestionIndex;

  private Boolean followUp = false;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void onCreate() {
    createdAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public InterviewSessionEntity getSession() {
    return session;
  }

  public void setSession(InterviewSessionEntity session) {
    this.session = session;
  }

  public Integer getQuestionIndex() {
    return questionIndex;
  }

  public void setQuestionIndex(Integer questionIndex) {
    this.questionIndex = questionIndex;
  }

  public String getQuestion() {
    return question;
  }

  public void setQuestion(String question) {
    this.question = question;
  }

  public String getSkillKey() {
    return skillKey;
  }

  public void setSkillKey(String skillKey) {
    this.skillKey = skillKey;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getStage() {
    return stage;
  }

  public void setStage(String stage) {
    this.stage = stage;
  }

  public String getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(String difficulty) {
    this.difficulty = difficulty;
  }

  public String getSourceType() {
    return sourceType;
  }

  public void setSourceType(String sourceType) {
    this.sourceType = sourceType;
  }

  public String getSourceRef() {
    return sourceRef;
  }

  public void setSourceRef(String sourceRef) {
    this.sourceRef = sourceRef;
  }

  public String getRequirementId() {
    return requirementId;
  }

  public void setRequirementId(String requirementId) {
    this.requirementId = requirementId;
  }

  public Integer getParentQuestionIndex() {
    return parentQuestionIndex;
  }

  public void setParentQuestionIndex(Integer parentQuestionIndex) {
    this.parentQuestionIndex = parentQuestionIndex;
  }

  public Boolean getFollowUp() {
    return followUp;
  }

  public void setFollowUp(Boolean followUp) {
    this.followUp = followUp;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
