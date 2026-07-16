package com.yzh666.careerai.modules.interview.service;

import com.yzh666.careerai.common.constant.CommonConstants.InterviewDefaults;
import com.yzh666.careerai.common.agent.tool.AgentInterviewDecision;
import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnEvaluation;
import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.common.model.AsyncTaskStatus;
import com.yzh666.careerai.modules.interview.model.HistoricalQuestion;
import com.yzh666.careerai.modules.interview.model.InterviewAnswerEntity;
import com.yzh666.careerai.modules.interview.model.InterviewBlueprintDTO;
import com.yzh666.careerai.modules.interview.model.InterviewCompletionSnapshotDTO;
import com.yzh666.careerai.modules.interview.model.InterviewQuestionDTO;
import com.yzh666.careerai.modules.interview.model.InterviewQuestionRecordEntity;
import com.yzh666.careerai.modules.interview.model.InterviewReportDTO;
import com.yzh666.careerai.modules.interview.model.InterviewSessionEntity;
import com.yzh666.careerai.modules.interview.model.TurnEvaluationEvidenceDTO;
import com.yzh666.careerai.modules.interview.repository.InterviewAnswerRepository;
import com.yzh666.careerai.modules.interview.repository.InterviewSessionRepository;
import com.yzh666.careerai.modules.interview.repository.InterviewQuestionRecordRepository;
import com.yzh666.careerai.modules.interview.skill.InterviewSkillService.CategoryDTO;
import com.yzh666.careerai.modules.resume.model.ResumeEntity;
import com.yzh666.careerai.modules.resume.repository.ResumeRepository;
import com.yzh666.careerai.modules.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 面试持久化服务
 * 面试会话和答案的持久化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewPersistenceService {
    
    private final InterviewSessionRepository sessionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final InterviewQuestionRecordRepository questionRecordRepository;
    private final ResumeRepository resumeRepository;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;
    
    /**
     * 保存新的面试会话（支持可选简历）
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewSessionEntity saveSession(String sessionId, Long resumeId,
                                              int totalQuestions,
                                              List<InterviewQuestionDTO> questions,
                                              String llmProvider,
                                              String skillId,
                                              String difficulty,
                                              Long jobId,
                                              Long matchReportId,
                                              InterviewBlueprintDTO blueprint,
                                              String agentCreationKey,
                                              String resumeTextSnapshot,
                                              String jdTextSnapshot,
                                              List<CategoryDTO> customCategories) {
        try {
            Long userId = currentUserService.currentUserId();
            InterviewSessionEntity session = new InterviewSessionEntity();
            session.setUserId(userId);
            session.setSessionId(sessionId);
            session.setTotalQuestions(totalQuestions);
            session.setPlannedMainQuestions(blueprint == null
                ? totalQuestions : blueprint.questionCount());
            session.setCurrentQuestionIndex(0);
            session.setStatus(InterviewSessionEntity.SessionStatus.CREATED);
            session.setQuestionsJson(objectMapper.writeValueAsString(questions));
            session.setLlmProvider(llmProvider != null ? llmProvider : "default");
            session.setSkillId(skillId != null ? skillId : InterviewDefaults.SKILL_ID);
            session.setDifficulty(difficulty != null ? difficulty : InterviewDefaults.DIFFICULTY);
            session.setJobId(jobId);
            session.setMatchReportId(matchReportId);
            session.setInterviewBlueprintJson(blueprint == null
                ? null : objectMapper.writeValueAsString(blueprint));
            session.setAgentCreationKey(agentCreationKey);
            session.setResumeTextSnapshot(resumeTextSnapshot);
            session.setJdTextSnapshot(jdTextSnapshot);
            session.setCustomCategoriesJson(writeNullableJson(customCategories));

            // 简历可选：有 resumeId 则关联简历
            if (resumeId != null) {
                ResumeEntity resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));
                session.setResume(resume);
            }

            InterviewSessionEntity saved = sessionRepository.save(session);
            questionRecordRepository.saveAll(questions.stream()
                .map(question -> toQuestionRecord(saved, question, difficulty, resumeId))
                .toList());
            log.info(
                "面试会话已保存: sessionId={}, userId={}, skillId={}, resumeId={}, jobId={}, matchReportId={}",
                sessionId,
                userId,
                skillId,
                resumeId,
                jobId,
                matchReportId
            );

            return saved;
        } catch (JacksonException e) {
            log.error("序列化问题列表失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存会话失败");
        }
    }

    /** 增量追加一道已经 Java 生成的题目，同步维护会话快照和题目明细。 */
    @Transactional(rollbackFor = Exception.class)
    public void appendQuestion(
            String sessionId,
            List<InterviewQuestionDTO> questions,
            InterviewQuestionDTO newQuestion,
            String difficulty) {
        InterviewSessionEntity session = findBySessionId(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        session.setQuestionsJson(writeJson(questions));
        session.setDifficulty(difficulty);
        sessionRepository.save(session);
        if (questionRecordRepository
                .findBySession_SessionIdAndQuestionIndex(sessionId, newQuestion.questionIndex())
                .isEmpty()) {
            questionRecordRepository.save(toQuestionRecord(
                session,
                newQuestion,
                difficulty,
                session.getResumeId()));
        }
    }

    public List<CategoryDTO> getCustomCategories(String sessionId) {
        InterviewSessionEntity session = findBySessionId(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        if (session.getCustomCategoriesJson() == null || session.getCustomCategoriesJson().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(session.getCustomCategoriesJson(), new TypeReference<>() {});
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取自定义面试分类失败");
        }
    }
    
    /**
     * 更新会话状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSessionStatus(String sessionId, InterviewSessionEntity.SessionStatus status) {
        Optional<InterviewSessionEntity> sessionOpt = findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            InterviewSessionEntity session = sessionOpt.get();
            session.setStatus(status);
            if (status == InterviewSessionEntity.SessionStatus.COMPLETED ||
                status == InterviewSessionEntity.SessionStatus.EVALUATED) {
                session.setCompletedAt(LocalDateTime.now());
            }
            sessionRepository.save(session);
        }
    }

    /**
     * 更新评估状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateEvaluateStatus(String sessionId, AsyncTaskStatus status, String error) {
        Optional<InterviewSessionEntity> sessionOpt = findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            InterviewSessionEntity session = sessionOpt.get();
            session.setEvaluateStatus(status);
            if (error != null) {
                session.setEvaluateError(error.length() > 500 ? error.substring(0, 500) : error);
            } else {
                session.setEvaluateError(null);
            }
            sessionRepository.save(session);
            log.debug("评估状态已更新: sessionId={}, status={}", sessionId, status);
        }
    }
    
    /**
     * 更新当前问题索引
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCurrentQuestionIndex(String sessionId, int index) {
        Optional<InterviewSessionEntity> sessionOpt = findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            InterviewSessionEntity session = sessionOpt.get();
            session.setCurrentQuestionIndex(index);
            session.setStatus(InterviewSessionEntity.SessionStatus.IN_PROGRESS);
            sessionRepository.save(session);
        }
    }
    
    /**
     * 保存面试答案
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewAnswerEntity saveAnswer(String sessionId, int questionIndex,
                                            String question, String category,
                                            String userAnswer, int score, String feedback) {
        return saveTurn(sessionId, questionIndex, question, category, userAnswer, score, feedback,
            null, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public InterviewAnswerEntity saveAdaptiveTurn(
            String sessionId,
            int questionIndex,
            String question,
            String category,
            String userAnswer,
            int score,
            String feedback,
            AgentInterviewTurnEvaluation evaluation,
            String agentAction,
            String decisionRationale) {
        return saveTurn(sessionId, questionIndex, question, category, userAnswer, score, feedback,
            evaluation, agentAction, decisionRationale);
    }

    /** 保存跳过、继续或结束操作；这些操作没有回答内容，也不会产生分数。 */
    @Transactional(rollbackFor = Exception.class)
    public InterviewAnswerEntity saveControlTurn(
            String sessionId,
            int questionIndex,
            String question,
            String category,
            String intent,
            String rationale) {
        return saveTurn(sessionId, questionIndex, question, category, null, null,
            "本轮未评分", null, intent, rationale);
    }

    private InterviewAnswerEntity saveTurn(String sessionId, int questionIndex,
                                            String question, String category,
                                            String userAnswer, Integer score, String feedback,
                                            AgentInterviewTurnEvaluation evaluation,
                                            String agentAction, String decisionRationale) {
        Optional<InterviewSessionEntity> sessionOpt = findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        InterviewAnswerEntity answer = answerRepository
            .findBySession_SessionIdAndQuestionIndex(sessionId, questionIndex)
            .orElseGet(() -> {
                InterviewAnswerEntity created = new InterviewAnswerEntity();
                created.setSession(sessionOpt.get());
                created.setQuestionIndex(questionIndex);
                return created;
            });

        answer.setQuestion(question);
        answer.setCategory(category);
        answer.setUserAnswer(userAnswer);
        answer.setScore(score);
        answer.setFeedback(feedback);
        answer.setEvaluationJson(writeNullableJson(evaluation));
        answer.setAgentAction(agentAction);
        answer.setDecisionRationale(decisionRationale);
        questionRecordRepository.findBySession_SessionIdAndQuestionIndex(sessionId, questionIndex)
            .ifPresent(answer::setQuestionRecord);

        InterviewAnswerEntity saved = answerRepository.save(answer);
        log.info("面试答案已保存: sessionId={}, questionIndex={}, score={}", 
                sessionId, questionIndex, score);
        
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeSession(
            String sessionId,
            InterviewSessionEntity.EndReason endReason,
            InterviewSessionEntity.CompletionType completionType,
            List<String> coveredTargets,
            List<String> unverifiedTargets) {
        InterviewSessionEntity session = findBySessionId(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        session.setStatus(InterviewSessionEntity.SessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        session.setEndReason(endReason);
        session.setCompletionType(completionType);
        session.setCoveredTargetsJson(writeJson(coveredTargets));
        session.setUnverifiedTargetsJson(writeJson(unverifiedTargets));
        sessionRepository.save(session);
    }

    public List<TurnEvaluationEvidenceDTO> getTurnEvaluations(String sessionId) {
        return answerRepository.findBySession_SessionIdOrderByQuestionIndex(sessionId).stream()
            .filter(answer -> answer.getEvaluationJson() != null
                && !answer.getEvaluationJson().isBlank())
            .map(answer -> new TurnEvaluationEvidenceDTO(
                answer.getQuestionIndex(),
                answer.getQuestion(),
                answer.getCategory(),
                answer.getFeedback(),
                readTurnEvaluation(answer.getEvaluationJson())))
            .filter(item -> item.evaluation() != null)
            .toList();
    }

    public InterviewCompletionSnapshotDTO getCompletionSnapshot(String sessionId) {
        InterviewSessionEntity session = findBySessionId(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        return new InterviewCompletionSnapshotDTO(
            session.getEndReason() == null ? null : session.getEndReason().name(),
            session.getCompletionType() == null ? null : session.getCompletionType().name(),
            readStringList(session.getCoveredTargetsJson()),
            readStringList(session.getUnverifiedTargetsJson())
        );
    }
    
    /**
     * 保存面试报告
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveReport(String sessionId, InterviewReportDTO report) {
        try {
            Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
            if (sessionOpt.isEmpty()) {
                log.warn("会话不存在: {}", sessionId);
                return;
            }

            InterviewSessionEntity session = sessionOpt.get();
            session.setOverallScore(report.overallScore());
            session.setOverallFeedback(report.overallFeedback());
            session.setStrengthsJson(objectMapper.writeValueAsString(report.strengths()));
            session.setImprovementsJson(objectMapper.writeValueAsString(report.improvements()));
            session.setJobEvaluationJson(report.jobEvaluation() == null
                ? null
                : objectMapper.writeValueAsString(report.jobEvaluation()));
            session.setReferenceAnswersJson(objectMapper.writeValueAsString(report.referenceAnswers()));
            session.setStatus(InterviewSessionEntity.SessionStatus.EVALUATED);
            session.setCompletedAt(LocalDateTime.now());

            sessionRepository.save(session);

            // 查询已存在的答案，建立索引
            List<InterviewAnswerEntity> existingAnswers = answerRepository.findBySession_SessionIdOrderByQuestionIndex(sessionId);
            java.util.Map<Integer, InterviewAnswerEntity> answerMap = existingAnswers.stream()
                .collect(java.util.stream.Collectors.toMap(
                    InterviewAnswerEntity::getQuestionIndex,
                    a -> a,
                    (a1, a2) -> a1
                ));

            // 建立参考答案索引
            java.util.Map<Integer, InterviewReportDTO.ReferenceAnswer> refAnswerMap = report.referenceAnswers().stream()
                .collect(java.util.stream.Collectors.toMap(
                    InterviewReportDTO.ReferenceAnswer::questionIndex,
                    r -> r,
                    (r1, r2) -> r1
                ));

            List<InterviewAnswerEntity> answersToSave = new java.util.ArrayList<>();

            // 遍历所有评估结果，更新或创建答案记录
            for (InterviewReportDTO.QuestionEvaluation eval : report.questionDetails()) {
                InterviewAnswerEntity answer = answerMap.get(eval.questionIndex());

                if (answer == null) {
                    // 未回答的题目，创建新记录
                    answer = new InterviewAnswerEntity();
                    answer.setSession(session);
                    answer.setQuestionIndex(eval.questionIndex());
                    answer.setQuestion(eval.question());
                    answer.setCategory(eval.category());
                    answer.setUserAnswer(null);  // 未回答
                    log.debug("为未回答的题目 {} 创建答案记录", eval.questionIndex());
                }

                // 更新评分和反馈
                answer.setScore(eval.score());
                answer.setFeedback(eval.feedback());

                // 设置参考答案和关键点
                InterviewReportDTO.ReferenceAnswer refAns = refAnswerMap.get(eval.questionIndex());
                if (refAns != null) {
                    answer.setReferenceAnswer(refAns.referenceAnswer());
                    if (refAns.keyPoints() != null && !refAns.keyPoints().isEmpty()) {
                        answer.setKeyPointsJson(objectMapper.writeValueAsString(refAns.keyPoints()));
                    }
                }

                answersToSave.add(answer);
            }

            answerRepository.saveAll(answersToSave);
            log.info("面试报告已保存: sessionId={}, score={}, 答案数={}",
                sessionId, report.overallScore(), answersToSave.size());

        } catch (JacksonException e) {
            log.error("序列化报告失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED, "保存面试报告失败");
        }
    }
    
    /**
     * 根据会话ID获取会话
     */
    public Optional<InterviewSessionEntity> findBySessionId(String sessionId) {
        return sessionRepository.findBySessionIdAndUserId(sessionId, currentUserService.currentUserId());
    }

    public Optional<InterviewSessionEntity> findByAgentCreationKey(String agentCreationKey) {
        return sessionRepository.findByAgentCreationKeyAndUserId(
            agentCreationKey,
            currentUserService.currentUserId());
    }
    
    /**
     * 获取简历的所有面试记录
     */
    public List<InterviewSessionEntity> findByResumeId(Long resumeId) {
        return sessionRepository.findByResumeIdAndUserIdOrderByCreatedAtDesc(resumeId, currentUserService.currentUserId());
    }

    /**
     * 获取所有面试记录（按创建时间倒序）
     */
    public List<InterviewSessionEntity> findAll() {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(currentUserService.currentUserId());
    }

    /**
     * 获取某个岗位关联的所有面试记录
     */
    public List<InterviewSessionEntity> findByJobId(Long jobId) {
        return sessionRepository.findByUserIdAndJobIdOrderByCreatedAtDesc(
            currentUserService.currentUserId(),
            jobId
        );
    }
    
    /**
     * 删除简历的所有面试会话
     * 由于InterviewSessionEntity设置了cascade = CascadeType.ALL, orphanRemoval = true
     * 删除会话会自动删除关联的答案
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSessionsByResumeId(Long resumeId) {
        List<InterviewSessionEntity> sessions = findByResumeId(resumeId);
        if (!sessions.isEmpty()) {
            sessionRepository.deleteAll(sessions);
            log.info("已删除 {} 个面试会话（包含所有答案）", sessions.size());
        }
    }
    
    /**
     * 删除单个面试会话
     * 由于InterviewSessionEntity设置了cascade = CascadeType.ALL, orphanRemoval = true
     * 删除会话会自动删除关联的答案
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSessionBySessionId(String sessionId) {
        Optional<InterviewSessionEntity> sessionOpt = findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            sessionRepository.delete(sessionOpt.get());
            log.info("已删除面试会话: sessionId={}", sessionId);
        } else {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }
    }
    
    /**
     * 查找未完成的面试会话（CREATED或IN_PROGRESS状态）
     */
    public Optional<InterviewSessionEntity> findUnfinishedSession(Long resumeId) {
        List<InterviewSessionEntity.SessionStatus> unfinishedStatuses = List.of(
            InterviewSessionEntity.SessionStatus.CREATED,
            InterviewSessionEntity.SessionStatus.IN_PROGRESS
        );
        return sessionRepository.findFirstByResumeIdAndUserIdAndStatusInOrderByCreatedAtDesc(
            resumeId,
            currentUserService.currentUserId(),
            unfinishedStatuses
        );
    }
    
    /**
     * 根据会话ID查找所有答案
     */
    public List<InterviewAnswerEntity> findAnswersBySessionId(String sessionId) {
        return answerRepository.findBySession_SessionIdOrderByQuestionIndex(sessionId);
    }

    /** 读取已执行的 Agent 面试决策，历史会话默认返回空列表。 */
    public List<AgentInterviewDecision> getAgentDecisions(String sessionId) {
        InterviewSessionEntity session = findBySessionId(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        if (session.getAgentDecisionsJson() == null || session.getAgentDecisionsJson().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(session.getAgentDecisionsJson(), new TypeReference<>() {});
        } catch (JacksonException e) {
            log.warn("读取 Agent 面试决策失败: sessionId={}, error={}", sessionId, e.getMessage());
            return List.of();
        }
    }

    /** 读取已校验的面试蓝图，兼容历史会话。 */
    public InterviewBlueprintDTO getInterviewBlueprint(String sessionId) {
        InterviewSessionEntity session = findBySessionId(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        if (session.getInterviewBlueprintJson() == null || session.getInterviewBlueprintJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(session.getInterviewBlueprintJson(), InterviewBlueprintDTO.class);
        } catch (JacksonException e) {
            log.warn("读取面试蓝图失败: sessionId={}, error={}", sessionId, e.getMessage());
            return null;
        }
    }

    /** 同一问题只保存一次决策，使重试具有业务幂等性。 */
    @Transactional(rollbackFor = Exception.class)
    public AgentInterviewDecision appendAgentDecision(
            String sessionId,
            AgentInterviewDecision decision) {
        InterviewSessionEntity session = findBySessionId(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        List<AgentInterviewDecision> decisions = new ArrayList<>(getAgentDecisions(sessionId));
        Optional<AgentInterviewDecision> existing = decisions.stream()
            .filter(item -> item.questionIndex() == decision.questionIndex())
            .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            decisions.add(decision);
            session.setAgentDecisionsJson(objectMapper.writeValueAsString(decisions));
            sessionRepository.save(session);
            return decision;
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存 Agent 面试决策失败");
        }
    }

    private InterviewQuestionRecordEntity toQuestionRecord(
            InterviewSessionEntity session,
            InterviewQuestionDTO question,
            String difficulty,
            Long resumeId) {
        InterviewQuestionRecordEntity record = new InterviewQuestionRecordEntity();
        record.setSession(session);
        record.setQuestionIndex(question.questionIndex());
        record.setQuestion(question.question());
        record.setSkillKey(question.type());
        record.setCategory(question.category());
        record.setDifficulty(difficulty);
        record.setFollowUp(question.isFollowUp());
        record.setParentQuestionIndex(question.parentQuestionIndex());
        record.setRequirementId(question.requirementId());
        if (question.isFollowUp()) {
            record.setStage("FOLLOW_UP");
        } else if (question.requirementId() != null) {
            record.setStage("JOB_REQUIREMENT");
        } else if (resumeId != null) {
            record.setStage("PROJECT_DEFENSE");
        } else {
            record.setStage("CORE_SKILL");
        }
        if (question.requirementId() != null) {
            record.setSourceType("JD_REQUIREMENT");
            record.setSourceRef(question.requirementId());
        } else if (resumeId != null) {
            record.setSourceType("RESUME");
            record.setSourceRef(String.valueOf(resumeId));
        } else {
            record.setSourceType("SKILL");
            record.setSourceRef(question.type());
        }
        return record;
    }

    private String writeNullableJson(Object value) {
        return value == null ? null : writeJson(value);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "序列化面试过程数据失败");
        }
    }

    private AgentInterviewTurnEvaluation readTurnEvaluation(String json) {
        try {
            return objectMapper.readValue(json, AgentInterviewTurnEvaluation.class);
        } catch (JacksonException e) {
            log.warn("读取单轮多维评价失败: {}", e.getMessage());
            return null;
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JacksonException e) {
            return List.of();
        }
    }

    private static final int MAX_HISTORICAL_QUESTIONS = 60;

    /**
     * 获取当前用户在同一面试方向下的历史提问。
     *
     * <p>不能只按 resumeId 查询：用户上传新版简历后数据库 ID 会变化，但已考知识点仍应延续，
     * 否则最显眼的项目亮点会在每个简历版本里反复成为首题。</p>
     */
    public List<HistoricalQuestion> getHistoricalQuestions(String skillId, Long resumeId) {
        List<InterviewSessionEntity> sessions =
            sessionRepository.findTop10ByUserIdAndSkillIdOrderByCreatedAtDesc(
                currentUserService.currentUserId(), skillId);

        log.info("加载跨简历版本历史题目: skillId={}, currentResumeId={}, 查到 {} 个历史会话",
            skillId, resumeId, sessions.size());

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<HistoricalQuestion> result = sessions.stream()
            .map(InterviewSessionEntity::getQuestionsJson)
            .filter(json -> json != null && !json.isEmpty())
            .flatMap(json -> {
                try {
                    List<InterviewQuestionDTO> questions = objectMapper.readValue(json,
                        new TypeReference<List<InterviewQuestionDTO>>() {});
                    return questions.stream()
                        .filter(q -> !q.isFollowUp())
                        .map(q -> new HistoricalQuestion(q.question(), q.type(), q.topicSummary()));
                } catch (Exception e) {
                    log.error("解析历史问题JSON失败", e);
                    return java.util.stream.Stream.<HistoricalQuestion>empty();
                }
            })
            .filter(hq -> seen.add(hq.question()))
            .limit(MAX_HISTORICAL_QUESTIONS)
            .toList();

        log.info("历史题目加载完成: 去重后 {} 道主问题，按分类: {}", result.size(),
            result.stream().collect(java.util.stream.Collectors.groupingBy(
                hq -> hq.type() != null ? hq.type() : "GENERAL",
                java.util.stream.Collectors.counting())));

        return result;
    }
}
