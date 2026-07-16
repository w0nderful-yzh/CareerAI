package com.yzh666.careerai.modules.interview.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 面试答案实体
 */
@Entity
@Table(name = "interview_answers",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_interview_answer_session_question", columnNames = {"session_id", "question_index"})
    },
    indexes = {
        @Index(name = "idx_interview_answer_session_question", columnList = "session_id,question_index")
    })
public class InterviewAnswerEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // 关联的会话
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSessionEntity session;
    
    // 问题索引
    @Column(name = "question_index")
    private Integer questionIndex;
    
    // 问题内容
    @Column(columnDefinition = "TEXT")
    private String question;
    
    // 问题类别
    private String category;
    
    // 用户答案
    @Column(columnDefinition = "TEXT")
    private String userAnswer;
    
    // 得分 (0-100)
    private Integer score;
    
    // 反馈
    @Column(columnDefinition = "TEXT")
    private String feedback;

    // Agent 单轮多维评价（JSON），维度不适用时保留 null。
    @Column(columnDefinition = "TEXT")
    private String evaluationJson;

    @Column(length = 32)
    private String agentAction;

    @Column(columnDefinition = "TEXT")
    private String decisionRationale;

    // 对应规范化问题；历史数据允许为空。
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_record_id")
    private InterviewQuestionRecordEntity questionRecord;
    
    // 参考答案
    @Column(columnDefinition = "TEXT")
    private String referenceAnswer;
    
    // 关键点 (JSON)
    @Column(columnDefinition = "TEXT")
    private String keyPointsJson;
    
    // 回答时间
    @Column(nullable = false)
    private LocalDateTime answeredAt;
    
    @PrePersist
    protected void onCreate() {
        answeredAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
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
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public String getUserAnswer() {
        return userAnswer;
    }
    
    public void setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer;
    }
    
    public Integer getScore() {
        return score;
    }
    
    public void setScore(Integer score) {
        this.score = score;
    }
    
    public String getFeedback() {
        return feedback;
    }
    
    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }
    
    public String getReferenceAnswer() {
        return referenceAnswer;
    }

    public String getEvaluationJson() {
        return evaluationJson;
    }

    public void setEvaluationJson(String evaluationJson) {
        this.evaluationJson = evaluationJson;
    }

    public String getAgentAction() {
        return agentAction;
    }

    public void setAgentAction(String agentAction) {
        this.agentAction = agentAction;
    }

    public String getDecisionRationale() {
        return decisionRationale;
    }

    public void setDecisionRationale(String decisionRationale) {
        this.decisionRationale = decisionRationale;
    }

    public InterviewQuestionRecordEntity getQuestionRecord() {
        return questionRecord;
    }

    public void setQuestionRecord(InterviewQuestionRecordEntity questionRecord) {
        this.questionRecord = questionRecord;
    }
    
    public void setReferenceAnswer(String referenceAnswer) {
        this.referenceAnswer = referenceAnswer;
    }
    
    public String getKeyPointsJson() {
        return keyPointsJson;
    }
    
    public void setKeyPointsJson(String keyPointsJson) {
        this.keyPointsJson = keyPointsJson;
    }
    
    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }
    
    public void setAnsweredAt(LocalDateTime answeredAt) {
        this.answeredAt = answeredAt;
    }
}
