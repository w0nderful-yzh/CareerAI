package com.yzh666.careerai.modules.interview.service;

import com.yzh666.careerai.common.agent.tool.AgentInterviewDecision;
import com.yzh666.careerai.common.agent.tool.AgentInterviewQuestion;
import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnEvaluation;
import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnCommand;
import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnResult;
import com.yzh666.careerai.common.agent.tool.AgentNextQuestionIntent;
import com.yzh666.careerai.common.constant.CommonConstants.InterviewDefaults;
import com.yzh666.careerai.common.ai.LlmProviderRegistry;
import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.common.model.AsyncTaskStatus;
import com.yzh666.careerai.infrastructure.redis.InterviewSessionCache;
import com.yzh666.careerai.infrastructure.redis.InterviewSessionCache.CachedSession;
import com.yzh666.careerai.modules.interview.listener.EvaluateStreamProducer;
import com.yzh666.careerai.modules.interview.model.CreateInterviewRequest;
import com.yzh666.careerai.modules.interview.model.InterviewBlueprintDTO;
import com.yzh666.careerai.modules.interview.model.HistoricalQuestion;
import com.yzh666.careerai.modules.interview.model.InterviewAnswerEntity;
import com.yzh666.careerai.modules.interview.model.InterviewQuestionDTO;
import com.yzh666.careerai.modules.interview.model.InterviewReportDTO;
import com.yzh666.careerai.modules.interview.model.InterviewSessionDTO;
import com.yzh666.careerai.modules.interview.model.InterviewSessionEntity;
import com.yzh666.careerai.modules.interview.model.InterviewSessionEntity.CompletionType;
import com.yzh666.careerai.modules.interview.model.InterviewSessionEntity.EndReason;
import com.yzh666.careerai.modules.interview.model.SubmitAnswerRequest;
import com.yzh666.careerai.modules.interview.model.SubmitAnswerResponse;
import com.yzh666.careerai.modules.interview.model.InterviewSessionDTO.SessionStatus;
import com.yzh666.careerai.modules.interview.skill.InterviewSkillService.CategoryDTO;
import com.yzh666.careerai.modules.jobmatch.service.JobMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 面试会话管理服务
 * 管理面试会话的生命周期，使用 Redis 缓存会话状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSessionService {

    private static final String ACTION_FOLLOW_UP = "FOLLOW_UP";
    private static final String ACTION_SWITCH_TOPIC = "SWITCH_TOPIC";
    private static final String ACTION_ADJUST_DIFFICULTY = "ADJUST_DIFFICULTY";
    private static final String ACTION_END_INTERVIEW = "END_INTERVIEW";
    private static final String INTENT_ANSWER = "ANSWER";
    private static final String INTENT_END = "END";
    private static final String INTENT_SKIP = "SKIP";
    private static final String INTENT_CONTINUE = "CONTINUE";
    private static final Set<String> CONTROL_INTENTS = Set.of(
        INTENT_END, INTENT_SKIP, INTENT_CONTINUE);
    private static final Set<String> BLUEPRINT_MODES = Set.of(
        "GENERAL", "JOB_TARGETED", "FOCUS_DRILL", "RESUME_DEFENSE");
    private static final Set<String> QUESTION_TYPES = Set.of(
        "CONCEPT", "PROJECT_EVIDENCE", "SCENARIO_DESIGN", "TROUBLESHOOTING");
    private static final Set<String> DIFFICULTIES = Set.of("junior", "mid", "senior");

    private final InterviewQuestionService questionService;
    private final AnswerEvaluationService evaluationService;
    private final InterviewPersistenceService persistenceService;
    private final InterviewSessionCache sessionCache;
    private final ObjectMapper objectMapper;
    private final EvaluateStreamProducer evaluateStreamProducer;
    private final LlmProviderRegistry llmProviderRegistry;
    private final JobMatchService jobMatchService;
    private final AbilityProfileService abilityProfileService;
    private final InterviewClosureService interviewClosureService;

    /**
     * 创建新的面试会话
     * 注意：如果已有未完成的会话，不会创建新的，而是返回现有会话
     * 前端应该先调用 findUnfinishedSession 检查，或者使用 forceCreate 参数强制创建
     */
    public InterviewSessionDTO createSession(CreateInterviewRequest request) {
        return createSession(request, null);
    }

    /** Agent 写 Tool 的幂等入口，同一用户、同一幂等键只会得到一个会话。 */
    public InterviewSessionDTO createSessionIdempotently(
            CreateInterviewRequest request,
            String idempotencyKey) {
        Optional<InterviewSessionEntity> existing = persistenceService
            .findByAgentCreationKey(idempotencyKey);
        if (existing.isPresent()) {
            return getSession(existing.get().getSessionId());
        }
        return createSession(request, idempotencyKey);
    }

    private InterviewSessionDTO createSession(CreateInterviewRequest request, String agentCreationKey) {
        // 如果指定了resumeId且未强制创建，检查是否有未完成的会话
        if (request.resumeId() != null && !Boolean.TRUE.equals(request.forceCreate())) {
            Optional<InterviewSessionDTO> unfinishedOpt = findUnfinishedSession(request.resumeId());
            if (unfinishedOpt.isPresent()) {
                log.info("检测到未完成的面试会话，返回现有会话: resumeId={}, sessionId={}",
                    request.resumeId(), unfinishedOpt.get().sessionId());
                return unfinishedOpt.get();
            }
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String skillId = request.skillId() != null ? request.skillId() : InterviewDefaults.SKILL_ID;
        InterviewBlueprintDTO blueprint = normalizeBlueprint(request);
        String difficulty = blueprint.difficulty();

        log.info("创建新面试会话: {}, skill: {}, difficulty: {}, questionCount: {}, resumeId: {}",
            sessionId, skillId, difficulty, request.questionCount(), request.resumeId());

        // 获取历史问题（通用模式按 skillId 查询，有简历时按 resumeId + skillId 精确匹配）
        List<HistoricalQuestion> historicalQuestions =
            persistenceService.getHistoricalQuestions(skillId, request.resumeId());
        String jobMatchContext = jobMatchService.buildInterviewContext(
            request.matchReportId(),
            request.jobId(),
            request.resumeId()
        );

        // Agent 会话只保存首题，后续每轮都根据真实回答增量出题。
        List<InterviewQuestionDTO> questions;
        if (agentCreationKey != null) {
            InterviewQuestionDTO first = questionService.generateOpeningQuestion(
                request.llmProvider(),
                skillId,
                difficulty,
                request.resumeText(),
                historicalQuestions,
                request.customCategories(),
                request.jdText(),
                jobMatchContext,
                blueprint
            );
            questions = List.of(InterviewQuestionDTO.create(
                0,
                first.question(),
                first.type(),
                first.category(),
                first.topicSummary(),
                false,
                null,
                first.requirementId()));
        } else {
            questions = questionService.generateQuestionsBySkill(
                request.llmProvider(),
                skillId,
                difficulty,
                request.resumeText(),
                request.questionCount(),
                historicalQuestions,
                request.customCategories(),
                request.jdText(),
                jobMatchContext,
                blueprint
            );
        }
        int totalQuestions = agentCreationKey == null ? questions.size() : blueprint.questionCount();

        // 保存到 Redis 缓存
        sessionCache.saveSession(
            sessionId,
            request.resumeText() != null ? request.resumeText() : "",
            request.resumeId(),
            questions,
            0,
            SessionStatus.CREATED
        );

        // 保存到数据库
        try {
            persistenceService.saveSession(sessionId, request.resumeId(),
                totalQuestions,
                questions,
                request.llmProvider(),
                skillId,
                difficulty,
                request.jobId(),
                request.matchReportId(),
                blueprint,
                agentCreationKey,
                request.resumeText(),
                request.jdText(),
                request.customCategories());
        } catch (Exception e) {
            log.warn("保存面试会话到数据库失败: {}", e.getMessage());
            if (agentCreationKey != null) {
                if (e instanceof BusinessException businessException) {
                    throw businessException;
                }
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存 Agent 面试会话失败");
            }
        }

        return new InterviewSessionDTO(
            sessionId,
            request.resumeText() != null ? request.resumeText() : "",
            totalQuestions,
            0,
            questions,
            SessionStatus.CREATED,
            blueprint,
            null,
            null,
            List.of(),
            List.of()
        );
    }

    /**
     * 获取会话信息（优先从缓存获取，缓存未命中则从数据库恢复）
     */
    public InterviewSessionDTO getSession(String sessionId) {
        // 1. 尝试从 Redis 缓存获取
        Optional<CachedSession> cachedOpt = sessionCache.getSession(sessionId);
        if (cachedOpt.isPresent()) {
            return toDTO(cachedOpt.get());
        }

        // 2. 缓存未命中，从数据库恢复
        CachedSession restoredSession = restoreSessionFromDatabase(sessionId);
        if (restoredSession == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        return toDTO(restoredSession);
    }

    /**
     * 查找并恢复未完成的面试会话
     */
    public Optional<InterviewSessionDTO> findUnfinishedSession(Long resumeId) {
        try {
            // 1. 先从 Redis 缓存查找
            Optional<String> cachedSessionIdOpt = sessionCache.findUnfinishedSessionId(resumeId);
            if (cachedSessionIdOpt.isPresent()) {
                String sessionId = cachedSessionIdOpt.get();
                Optional<CachedSession> cachedOpt = sessionCache.getSession(sessionId);
                if (cachedOpt.isPresent()) {
                    log.debug("从 Redis 缓存找到未完成会话: resumeId={}, sessionId={}", resumeId, sessionId);
                    return Optional.of(toDTO(cachedOpt.get()));
                }
            }

            // 2. 缓存未命中，从数据库查找
            Optional<InterviewSessionEntity> entityOpt = persistenceService.findUnfinishedSession(resumeId);
            if (entityOpt.isEmpty()) {
                return Optional.empty();
            }

            InterviewSessionEntity entity = entityOpt.get();
            CachedSession restoredSession = restoreSessionFromEntity(entity);
            if (restoredSession != null) {
                return Optional.of(toDTO(restoredSession));
            }
        } catch (Exception e) {
            log.error("恢复未完成会话失败: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * 查找并恢复未完成的面试会话，如果不存在则抛出异常
     */
    public InterviewSessionDTO findUnfinishedSessionOrThrow(Long resumeId) {
        return findUnfinishedSession(resumeId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "未找到未完成的面试会话"));
    }

    /**
     * 从数据库恢复会话并缓存到 Redis
     */
    private CachedSession restoreSessionFromDatabase(String sessionId) {
        try {
            Optional<InterviewSessionEntity> entityOpt = persistenceService.findBySessionId(sessionId);
            return entityOpt.map(this::restoreSessionFromEntity).orElse(null);
        } catch (Exception e) {
            log.error("从数据库恢复会话失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从实体恢复会话并缓存到 Redis
     */
    private CachedSession restoreSessionFromEntity(InterviewSessionEntity entity) {
        try {
            // 解析问题列表
            List<InterviewQuestionDTO> questions = objectMapper.readValue(
                entity.getQuestionsJson(),
                new TypeReference<>() {}
            );

            // 恢复已保存的答案
            List<InterviewAnswerEntity> answers = persistenceService.findAnswersBySessionId(entity.getSessionId());
            for (InterviewAnswerEntity answer : answers) {
                int index = answer.getQuestionIndex();
                if (index >= 0 && index < questions.size()) {
                    InterviewQuestionDTO question = questions.get(index);
                    questions.set(index, question.withAnswer(answer.getUserAnswer()));
                }
            }

            SessionStatus status = convertStatus(entity.getStatus());

            // 保存到 Redis 缓存
            sessionCache.saveSession(
                entity.getSessionId(),
                entity.getResumeTextSnapshot() != null
                    ? entity.getResumeTextSnapshot()
                    : entity.getResume() != null ? entity.getResume().getResumeText() : "",
                entity.getResume() != null ? entity.getResume().getId() : null,
                questions,
                entity.getCurrentQuestionIndex(),
                status
            );

            log.info("从数据库恢复会话到 Redis: sessionId={}, currentIndex={}, status={}",
                entity.getSessionId(), entity.getCurrentQuestionIndex(), entity.getStatus());

            // 返回缓存的会话
            return sessionCache.getSession(entity.getSessionId()).orElse(null);
        } catch (Exception e) {
            log.error("恢复会话失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private SessionStatus convertStatus(InterviewSessionEntity.SessionStatus status) {
        return switch (status) {
            case CREATED -> SessionStatus.CREATED;
            case IN_PROGRESS -> SessionStatus.IN_PROGRESS;
            case COMPLETED -> SessionStatus.COMPLETED;
            case EVALUATED -> SessionStatus.EVALUATED;
        };
    }

    /**
     * 获取当前问题的响应（包含完成状态）
     */
    public Map<String, Object> getCurrentQuestionResponse(String sessionId) {
        InterviewQuestionDTO question = getCurrentQuestion(sessionId);
        if (question == null) {
            return Map.of(
                "completed", true,
                "message", "所有问题已回答完毕"
            );
        }
        return Map.of(
            "completed", false,
            "question", question
        );
    }

    /**
     * 获取当前问题
     */
    public InterviewQuestionDTO getCurrentQuestion(String sessionId) {
        CachedSession session = getOrRestoreSession(sessionId);
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        if (session.getCurrentIndex() >= questions.size()) {
            return null; // 所有问题已回答完
        }

        // 更新状态为进行中
        if (session.getStatus() == SessionStatus.CREATED) {
            session.setStatus(SessionStatus.IN_PROGRESS);
            sessionCache.updateSessionStatus(sessionId, SessionStatus.IN_PROGRESS);

            // 同步到数据库
            try {
                persistenceService.updateSessionStatus(sessionId,
                    InterviewSessionEntity.SessionStatus.IN_PROGRESS);
            } catch (Exception e) {
                log.warn("更新会话状态失败: {}", e.getMessage());
            }
        }

        return questions.get(session.getCurrentIndex());
    }

    /** 执行并持久化一轮 Agent 面试决策。 */
    public AgentInterviewTurnResult applyAdaptiveTurn(
            String sessionId,
            AgentInterviewTurnCommand command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "面试决策参数不能为空");
        }
        String intent = normalizeIntent(command.intent());
        if (!INTENT_ANSWER.equals(intent)) {
            return applyControlTurn(sessionId, command, intent);
        }
        requireAdaptiveCommand(command);
        Optional<AgentInterviewDecision> existing = persistenceService.getAgentDecisions(sessionId).stream()
            .filter(item -> item.questionIndex() == command.questionIndex())
            .findFirst();
        if (existing.isPresent()) {
            return rebuildAdaptiveResult(sessionId, existing.get());
        }

        CachedSession session = getOrRestoreSession(sessionId);
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);
        int currentIndex = session.getCurrentIndex();
        if (command.questionIndex() != currentIndex || currentIndex >= questions.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能回答当前面试问题");
        }

        InterviewQuestionDTO current = questions.get(currentIndex);
        InterviewSessionEntity entity = persistenceService.findBySessionId(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        InterviewBlueprintDTO blueprint = persistenceService.getInterviewBlueprint(sessionId);
        boolean requestedEnd = ACTION_END_INTERVIEW.equals(command.action());
        InterviewQuestionDTO target = null;
        if (!requestedEnd && questions.size() < entity.getTotalQuestions()) {
            validateNextQuestionIntent(command, current, questions, blueprint, entity.getDifficulty());
            target = generateNextQuestion(
                entity, blueprint, command.nextQuestionIntent(), current, command.answer(), questions);
            questions.add(target);
        } else if (!requestedEnd) {
            validateNextQuestionIntent(command, current, questions, blueprint, entity.getDifficulty());
        } else if (command.nextQuestionIntent() != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束面试时不能携带下一题意图");
        }
        InterviewQuestionDTO answered = current.withAnswer(command.answer())
            .withEvaluation(command.answerScore(), command.feedback());
        questions.set(currentIndex, answered);

        InterviewAnswerEntity savedAnswer = persistenceService.saveAdaptiveTurn(
            sessionId,
            currentIndex,
            current.question(),
            current.category(),
            command.answer(),
            command.answerScore(),
            command.feedback(),
            command.evaluation(),
            command.action(),
            command.rationale()
        );
        abilityProfileService.recordTurn(sessionId, current, savedAnswer, command.evaluation());
        if (target != null) {
            persistenceService.appendQuestion(
                sessionId, questions, target, command.nextQuestionIntent().difficulty());
        }
        sessionCache.updateQuestions(sessionId, questions);

        boolean completed = requestedEnd || target == null;
        Integer targetIndex = target == null ? null : target.questionIndex();
        String executedAction = completed ? ACTION_END_INTERVIEW : command.action();
        String executedRationale = !requestedEnd && completed
            ? command.rationale().trim() + "；已达面试题目预算"
            : command.rationale().trim();
        AgentInterviewDecision decision = new AgentInterviewDecision(
            currentIndex,
            executedAction,
            executedRationale,
            command.answerScore(),
            command.feedback().trim(),
            command.difficultyAdjustment(),
            targetIndex,
            target == null ? null : target.requirementId(),
            java.time.LocalDateTime.now()
        );
        decision = persistenceService.appendAgentDecision(sessionId, decision);

        if (completed) {
            finishInterview(
                sessionId,
                questions,
                currentIndex + 1,
                requestedEnd ? resolveEndReason(command.endReason()) : EndReason.QUESTION_LIMIT
            );
        } else {
            sessionCache.updateCurrentIndex(sessionId, targetIndex);
            sessionCache.updateSessionStatus(sessionId, SessionStatus.IN_PROGRESS);
            persistenceService.updateCurrentQuestionIndex(sessionId, targetIndex);
        }

        int answeredCount = (int) questions.stream()
            .filter(question -> question.userAnswer() != null && !question.userAnswer().isBlank())
            .count();
        return new AgentInterviewTurnResult(
            sessionId,
            completed,
            target == null ? null : toAgentQuestion(target),
            decision,
            answeredCount,
            entity.getTotalQuestions()
        );
    }

    /**
     * 控制意图不进入评分模型。跳过/继续由 Java 选择下一道主问题，结束操作直接统一收尾。
     */
    private AgentInterviewTurnResult applyControlTurn(
            String sessionId,
            AgentInterviewTurnCommand command,
            String intent) {
        if (!CONTROL_INTENTS.contains(intent)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的面试意图");
        }
        if (command.questionIndex() < 0 || command.rationale() == null
                || command.rationale().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "面试控制参数无效");
        }
        CachedSession session = getOrRestoreSession(sessionId);
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);
        if (session.getStatus() == SessionStatus.COMPLETED
                || session.getStatus() == SessionStatus.EVALUATED) {
            return controlResult(sessionId, intent, true, null, questions);
        }

        int currentIndex = session.getCurrentIndex();
        if (command.questionIndex() != currentIndex || currentIndex >= questions.size()) {
            boolean alreadyApplied = persistenceService.findAnswersBySessionId(sessionId).stream()
                .anyMatch(answer -> answer.getQuestionIndex() == command.questionIndex()
                    && intent.equals(answer.getAgentAction()));
            if (alreadyApplied) {
                InterviewQuestionDTO current = currentIndex < questions.size()
                    ? questions.get(currentIndex) : null;
                return controlResult(sessionId, intent, current == null, current, questions);
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能操作当前面试问题");
        }

        InterviewQuestionDTO current = questions.get(currentIndex);
        if (INTENT_END.equals(intent)) {
            persistenceService.saveControlTurn(
                sessionId, currentIndex, current.question(), current.category(), intent,
                command.rationale());
            finishInterview(sessionId, questions, currentIndex, EndReason.USER_REQUESTED);
            return controlResult(sessionId, intent, true, null, questions);
        }

        InterviewQuestionDTO next = questions.stream()
            .skip(currentIndex + 1L)
            .filter(question -> !question.isFollowUp())
            .filter(question -> question.userAnswer() == null || question.userAnswer().isBlank())
            .findFirst()
            .orElse(null);
        InterviewSessionEntity entity = persistenceService.findBySessionId(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        if (next == null && entity.getAgentCreationKey() != null
                && questions.size() < entity.getTotalQuestions()) {
            InterviewBlueprintDTO blueprint = persistenceService.getInterviewBlueprint(sessionId);
            AgentNextQuestionIntent nextIntent = buildControlNextQuestionIntent(
                blueprint, entity.getDifficulty(), current, questions, intent);
            next = generateNextQuestion(entity, blueprint, nextIntent, current, "", questions);
            questions.add(next);
            persistenceService.appendQuestion(
                sessionId, questions, next, nextIntent.difficulty());
            sessionCache.updateQuestions(sessionId, questions);
        }
        persistenceService.saveControlTurn(
            sessionId, currentIndex, current.question(), current.category(), intent,
            command.rationale());
        if (next == null) {
            finishInterview(sessionId, questions, currentIndex + 1, EndReason.QUESTION_LIMIT);
            return controlResult(sessionId, intent, true, null, questions);
        }

        sessionCache.updateCurrentIndex(sessionId, next.questionIndex());
        sessionCache.updateSessionStatus(sessionId, SessionStatus.IN_PROGRESS);
        persistenceService.updateCurrentQuestionIndex(sessionId, next.questionIndex());
        return controlResult(sessionId, intent, false, next, questions);
    }

    private AgentInterviewTurnResult controlResult(
            String sessionId,
            String intent,
            boolean completed,
            InterviewQuestionDTO next,
            List<InterviewQuestionDTO> questions) {
        int answeredCount = (int) questions.stream()
            .filter(question -> question.userAnswer() != null && !question.userAnswer().isBlank())
            .count();
        return new AgentInterviewTurnResult(
            sessionId,
            completed,
            next == null ? null : toAgentQuestion(next),
            null,
            answeredCount,
            persistenceService.findBySessionId(sessionId)
                .map(InterviewSessionEntity::getTotalQuestions)
                .orElse(questions.size())
        );
    }

    private String normalizeIntent(String value) {
        return value == null || value.isBlank() ? INTENT_ANSWER : value.trim().toUpperCase();
    }

    private void requireAdaptiveCommand(AgentInterviewTurnCommand command) {
        if (command == null || command.questionIndex() < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "面试决策参数无效");
        }
        if (command.answer() == null || command.answer().isBlank() || command.answer().length() > 12000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "答案不能为空且不能超过 12000 字");
        }
        if (command.rationale() == null || command.rationale().isBlank()
                || command.feedback() == null || command.feedback().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "决策依据和回答反馈不能为空");
        }
        if (command.answerScore() < 0 || command.answerScore() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "回答得分必须在 0 到 100 之间");
        }
        if (!List.of(ACTION_FOLLOW_UP, ACTION_SWITCH_TOPIC, ACTION_ADJUST_DIFFICULTY,
                ACTION_END_INTERVIEW).contains(command.action())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的面试决策动作");
        }
        if (!List.of("KEEP", "HARDER", "EASIER").contains(command.difficultyAdjustment())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的难度调整动作");
        }
        validateTurnEvaluation(command.evaluation(), command.answer());
        if (!ACTION_END_INTERVIEW.equals(command.action())
                && command.endReason() != null && !command.endReason().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅结束面试时允许填写 endReason");
        }
    }

    private void validateNextQuestionIntent(
            AgentInterviewTurnCommand command,
            InterviewQuestionDTO current,
            List<InterviewQuestionDTO> questions,
            InterviewBlueprintDTO blueprint,
            String currentDifficulty) {
        AgentNextQuestionIntent intent = command.nextQuestionIntent();
        if (intent == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "继续面试时必须提供下一题意图");
        }
        if (intent.questionType() == null || intent.topic() == null || intent.topic().isBlank()
                || intent.objective() == null || intent.objective().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "下一题的题型、主题和考察目标不能为空");
        }
        if (intent.topic().length() > 120 || intent.objective().length() > 500) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "下一题意图文本超出长度限制");
        }
        Set<String> allowedTypes = blueprint == null
            ? QUESTION_TYPES : new LinkedHashSet<>(blueprint.questionTypes());
        if (!allowedTypes.contains(intent.questionType().toUpperCase())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "下一题题型不在面试蓝图中");
        }
        if (!DIFFICULTIES.contains(intent.difficulty())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "下一题难度无效");
        }
        if (intent.requirementId() != null && (blueprint == null
                || !blueprint.targetRequirementIds().contains(intent.requirementId()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "下一题关联的岗位要求不在蓝图中");
        }

        int expectedParent = current.isFollowUp() && current.parentQuestionIndex() != null
            ? current.parentQuestionIndex() : current.questionIndex();
        if (ACTION_FOLLOW_UP.equals(command.action())) {
            if (!intent.followUp() || !Integer.valueOf(expectedParent).equals(intent.parentQuestionIndex())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "追问必须关联当前主问题");
            }
            int maxFollowUps = blueprint == null ? 2 : blueprint.maxFollowUpsPerTopic();
            long existingFollowUps = questions.stream()
                .filter(InterviewQuestionDTO::isFollowUp)
                .filter(question -> Integer.valueOf(expectedParent).equals(question.parentQuestionIndex()))
                .count();
            if (existingFollowUps >= maxFollowUps) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "当前主题已达最大追问次数");
            }
        } else if (intent.followUp() || intent.parentQuestionIndex() != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非追问动作不能关联主问题索引");
        }
        if (ACTION_SWITCH_TOPIC.equals(command.action()) && intent.followUp()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "换方向动作必须生成主问题");
        }
        if (ACTION_ADJUST_DIFFICULTY.equals(command.action())) {
            if ("KEEP".equals(command.difficultyAdjustment())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "调整难度时必须选择提高或降低");
            }
            int currentRank = difficultyRank(currentDifficulty);
            int nextRank = difficultyRank(intent.difficulty());
            if ("HARDER".equals(command.difficultyAdjustment()) && nextRank <= currentRank
                    || "EASIER".equals(command.difficultyAdjustment()) && nextRank >= currentRank) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "难度调整与下一题难度不一致");
            }
        }
    }

    private int difficultyRank(String difficulty) {
        return switch (difficulty) {
            case "junior" -> 0;
            case "senior" -> 2;
            default -> 1;
        };
    }

    private InterviewQuestionDTO generateNextQuestion(
            InterviewSessionEntity entity,
            InterviewBlueprintDTO blueprint,
            AgentNextQuestionIntent intent,
            InterviewQuestionDTO current,
            String currentAnswer,
            List<InterviewQuestionDTO> questions) {
        String resumeText = entity.getResumeTextSnapshot();
        if ((resumeText == null || resumeText.isBlank()) && entity.getResume() != null) {
            resumeText = entity.getResume().getResumeText();
        }
        String jobMatchContext = jobMatchService.buildInterviewContext(
            entity.getMatchReportId(), entity.getJobId(), entity.getResumeId());
        return questionService.generateNextQuestion(
            entity.getLlmProvider(),
            entity.getSkillId(),
            resumeText,
            persistenceService.getCustomCategories(entity.getSessionId()),
            entity.getJdTextSnapshot(),
            jobMatchContext,
            blueprint,
            intent,
            current,
            currentAnswer,
            questions);
    }

    private AgentNextQuestionIntent buildControlNextQuestionIntent(
            InterviewBlueprintDTO blueprint,
            String difficulty,
            InterviewQuestionDTO current,
            List<InterviewQuestionDTO> questions,
            String intent) {
        List<String> questionTypes = blueprint == null || blueprint.questionTypes().isEmpty()
            ? List.copyOf(QUESTION_TYPES) : blueprint.questionTypes();
        List<String> focusTopics = blueprint == null ? List.of() : blueprint.focusTopics();
        String topic = focusTopics.stream()
            .filter(value -> !matchesFocus(current, value))
            .findFirst()
            .orElseGet(() -> focusTopics.isEmpty() ? current.category() : focusTopics.getFirst());
        String requirementId = blueprint == null || blueprint.targetRequirementIds().isEmpty()
            ? null : blueprint.targetRequirementIds().getFirst();
        return new AgentNextQuestionIntent(
            questionTypes.get(questions.size() % questionTypes.size()),
            topic,
            requirementId,
            difficulty,
            false,
            null,
            INTENT_SKIP.equals(intent)
                ? "跳过当前题后切换到蓝图中尚未充分验证的主题"
                : "继续按面试蓝图验证尚未充分覆盖的能力");
    }

    private void validateTurnEvaluation(
            AgentInterviewTurnEvaluation evaluation,
            String answer) {
        if (evaluation == null || !evaluation.answered()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前回答必须包含多维评价");
        }
        List<Integer> scores = List.of(
            nullableScore(evaluation.technicalCorrectness()),
            nullableScore(evaluation.technicalDepth()),
            nullableScore(evaluation.completeness()),
            nullableScore(evaluation.scenarioReasoning()),
            nullableScore(evaluation.projectUnderstanding()),
            nullableScore(evaluation.troubleshooting()),
            nullableScore(evaluation.expressionStructure()),
            nullableScore(evaluation.clarity()),
            nullableScore(evaluation.credibility()),
            nullableScore(evaluation.jobRelevance()),
            evaluation.confidence()
        );
        if (scores.stream().anyMatch(score -> score < 0 || score > 100)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "多维评价分数必须在 0 到 100 之间");
        }
        if (evaluation.evidenceSnippets() == null || evaluation.evidenceSnippets().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "多维评价至少需要一条回答证据");
        }
        if (evaluation.evidenceSnippets().size() > 5
                || evaluation.missingPoints() != null && evaluation.missingPoints().size() > 8
                || evaluation.errors() != null && evaluation.errors().size() > 8) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "多维评价条目数量超出限制");
        }
        for (String snippet : evaluation.evidenceSnippets()) {
            if (snippet == null || snippet.isBlank() || snippet.length() > 300
                    || !answer.contains(snippet.trim())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "评价证据必须逐字来自当前回答");
            }
        }
    }

    private int nullableScore(Integer score) {
        return score == null ? 0 : score;
    }

    private AgentInterviewTurnResult rebuildAdaptiveResult(
            String sessionId,
            AgentInterviewDecision decision) {
        InterviewSessionDTO session = getSession(sessionId);
        AgentInterviewQuestion next = decision.targetQuestionIndex() == null
            ? null
            : session.questions().stream()
                .filter(question -> question.questionIndex() == decision.targetQuestionIndex())
                .findFirst()
                .map(this::toAgentQuestion)
                .orElse(null);
        int answeredCount = (int) session.questions().stream()
            .filter(question -> question.userAnswer() != null && !question.userAnswer().isBlank())
            .count();
        return new AgentInterviewTurnResult(
            sessionId,
            ACTION_END_INTERVIEW.equals(decision.action()),
            next,
            decision,
            answeredCount,
            session.totalQuestions()
        );
    }

    private AgentInterviewQuestion toAgentQuestion(InterviewQuestionDTO question) {
        return new AgentInterviewQuestion(
            question.questionIndex(),
            question.question(),
            question.type(),
            question.category(),
            question.topicSummary(),
            question.userAnswer(),
            question.score(),
            question.feedback(),
            question.isFollowUp(),
            question.parentQuestionIndex(),
            question.requirementId()
        );
    }

    /**
     * 提交答案（并进入下一题）
     * 如果是最后一题，自动触发异步评估
     */
    public SubmitAnswerResponse submitAnswer(SubmitAnswerRequest request) {
        CachedSession session = getOrRestoreSession(request.sessionId());
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        int index = request.questionIndex();
        if (index < 0 || index >= questions.size()) {
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND, "无效的问题索引: " + index);
        }

        // 更新问题答案
        InterviewQuestionDTO question = questions.get(index);
        InterviewQuestionDTO answeredQuestion = question.withAnswer(request.answer());
        questions.set(index, answeredQuestion);

        // 移动到下一题
        int newIndex = index + 1;

        // 检查是否全部完成
        boolean hasNextQuestion = newIndex < questions.size();
        InterviewQuestionDTO nextQuestion = hasNextQuestion ? questions.get(newIndex) : null;

        SessionStatus newStatus = hasNextQuestion ? SessionStatus.IN_PROGRESS : SessionStatus.COMPLETED;

        persistSubmittedAnswer(request, index, question, newIndex, newStatus);

        // 更新 Redis 缓存。DB 已经持久化成功，缓存失败时可由后续读取从数据库恢复。
        sessionCache.updateQuestions(request.sessionId(), questions);
        if (newStatus == SessionStatus.COMPLETED) {
            finishInterview(request.sessionId(), questions, newIndex, EndReason.QUESTION_LIMIT);
        } else {
            sessionCache.updateCurrentIndex(request.sessionId(), newIndex);
        }

        log.info("会话 {} 提交答案: 问题{}, 剩余{}题",
            request.sessionId(), index, questions.size() - newIndex);

        return new SubmitAnswerResponse(
            hasNextQuestion,
            nextQuestion,
            newIndex,
            questions.size()
        );
    }

    private void persistSubmittedAnswer(SubmitAnswerRequest request, int index,
                                        InterviewQuestionDTO question, int newIndex,
                                        SessionStatus newStatus) {
        try {
            persistenceService.saveAnswer(
                request.sessionId(), index,
                question.question(), question.category(),
                request.answer(), 0, null  // 分数在报告生成时更新
            );
            persistenceService.updateCurrentQuestionIndex(request.sessionId(), newIndex);
            persistenceService.updateSessionStatus(request.sessionId(),
                newStatus == SessionStatus.COMPLETED
                    ? InterviewSessionEntity.SessionStatus.COMPLETED
                    : InterviewSessionEntity.SessionStatus.IN_PROGRESS);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("保存答案到数据库失败: sessionId={}, questionIndex={}",
                request.sessionId(), index, e);
            throw new BusinessException(ErrorCode.INTERVIEW_ANSWER_SAVE_FAILED,
                "保存答案失败，请稍后重试");
        }
    }

    private void enqueueEvaluationTask(String sessionId) {
        persistenceService.updateEvaluateStatus(sessionId, AsyncTaskStatus.PENDING, null);
        evaluateStreamProducer.sendEvaluateTask(sessionId);
        log.info("会话 {} 已完成所有问题，评估任务已入队", sessionId);
    }

    /**
     * 暂存答案（不进入下一题）
     */
    public void saveAnswer(SubmitAnswerRequest request) {
        CachedSession session = getOrRestoreSession(request.sessionId());
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        int index = request.questionIndex();
        if (index < 0 || index >= questions.size()) {
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND, "无效的问题索引: " + index);
        }

        // 更新问题答案
        InterviewQuestionDTO question = questions.get(index);
        InterviewQuestionDTO answeredQuestion = question.withAnswer(request.answer());
        questions.set(index, answeredQuestion);

        // 更新 Redis 缓存
        sessionCache.updateQuestions(request.sessionId(), questions);

        // 更新状态为进行中
        if (session.getStatus() == SessionStatus.CREATED) {
            sessionCache.updateSessionStatus(request.sessionId(), SessionStatus.IN_PROGRESS);
        }

        // 保存答案到数据库（不更新currentIndex）
        try {
            persistenceService.saveAnswer(
                request.sessionId(), index,
                question.question(), question.category(),
                request.answer(), 0, null
            );
            persistenceService.updateSessionStatus(request.sessionId(),
                InterviewSessionEntity.SessionStatus.IN_PROGRESS);
        } catch (Exception e) {
            log.warn("暂存答案到数据库失败: {}", e.getMessage());
        }

        log.info("会话 {} 暂存答案: 问题{}", request.sessionId(), index);
    }

    /**
     * 提前交卷（触发异步评估）
     */
    public void completeInterview(String sessionId) {
        CachedSession session = getOrRestoreSession(sessionId);

        if (session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.EVALUATED) {
            return;
        }

        finishInterview(
            sessionId,
            session.getQuestions(objectMapper),
            session.getCurrentIndex(),
            EndReason.MANUAL_BUTTON
        );

        log.info("会话 {} 提前交卷，已完成统一收尾", sessionId);
    }

    /**
     * 获取或恢复会话（优先从缓存获取）
     */
    private CachedSession getOrRestoreSession(String sessionId) {
        // 1. 尝试从 Redis 缓存获取
        Optional<CachedSession> cachedOpt = sessionCache.getSession(sessionId);
        if (cachedOpt.isPresent()) {
            // 刷新 TTL
            sessionCache.refreshSessionTTL(sessionId);
            return cachedOpt.get();
        }

        // 2. 缓存未命中，从数据库恢复
        CachedSession restoredSession = restoreSessionFromDatabase(sessionId);
        if (restoredSession == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        return restoredSession;
    }

    /**
     * 生成评估报告
     */
    public InterviewReportDTO generateReport(String sessionId) {
        CachedSession session = getOrRestoreSession(sessionId);

        if (session.getStatus() != SessionStatus.COMPLETED && session.getStatus() != SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_COMPLETED, "面试尚未完成，无法生成报告");
        }

        log.info("生成面试报告: {}", sessionId);

        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);
        // 自适应面试会主动跳过低价值追问，报告只评价真正回答过的问题，避免把 Agent 的换题决策算成 0 分。
        if (!persistenceService.getAgentDecisions(sessionId).isEmpty()) {
            questions = questions.stream()
                .filter(question -> question.userAnswer() != null && !question.userAnswer().isBlank())
                .toList();
        }

        // 获取 LLM 客户端
        String provider = null;
        Optional<InterviewSessionEntity> entityOpt = persistenceService.findBySessionId(sessionId);
        if (entityOpt.isPresent()) {
            provider = entityOpt.get().getLlmProvider();
        }
        ChatClient chatClient = llmProviderRegistry.getChatClientOrDefault(provider);

        InterviewReportDTO report = evaluationService.evaluateInterview(
            chatClient,
            sessionId,
            session.getResumeText(),
            questions
        );

        // 更新 Redis 缓存状态
        sessionCache.updateSessionStatus(sessionId, SessionStatus.EVALUATED);

        // 保存报告到数据库
        try {
            persistenceService.saveReport(sessionId, report);
            interviewClosureService.finalizeSession(sessionId, report);
        } catch (Exception e) {
            log.warn("保存报告或生成结束产物失败: {}", e.getMessage());
        }

        return report;
    }

    /**
     * 将缓存会话转换为 DTO
     */
    private InterviewSessionDTO toDTO(CachedSession session) {
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);
        Optional<InterviewSessionEntity> entity = persistenceService.findBySessionId(session.getSessionId());
        return new InterviewSessionDTO(
            session.getSessionId(),
            session.getResumeText(),
            entity.map(InterviewSessionEntity::getTotalQuestions).orElse(questions.size()),
            session.getCurrentIndex(),
            questions,
            session.getStatus(),
            persistenceService.getInterviewBlueprint(session.getSessionId()),
            entity.map(InterviewSessionEntity::getEndReason).map(Enum::name).orElse(null),
            entity.map(InterviewSessionEntity::getCompletionType).map(Enum::name).orElse(null),
            entity.map(item -> readStringList(item.getCoveredTargetsJson())).orElse(List.of()),
            entity.map(item -> readStringList(item.getUnverifiedTargetsJson())).orElse(List.of())
        );
    }

    private void completeSession(
            String sessionId,
            List<InterviewQuestionDTO> questions,
            EndReason endReason) {
        InterviewBlueprintDTO blueprint = persistenceService.getInterviewBlueprint(sessionId);
        Set<String> requirementTargets = blueprint == null
            ? Set.of()
            : new LinkedHashSet<>(blueprint.targetRequirementIds());
        List<InterviewQuestionDTO> answeredQuestions = questions.stream()
            .filter(question -> question.userAnswer() != null && !question.userAnswer().isBlank())
            .toList();
        Set<String> covered = answeredQuestions.stream()
            .map(InterviewQuestionDTO::requirementId)
            .filter(id -> id != null && !id.isBlank())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        covered.retainAll(requirementTargets);
        Set<String> unverified = new LinkedHashSet<>(requirementTargets);
        unverified.removeAll(covered);
        List<String> focusTopics = blueprint == null ? List.of() : blueprint.focusTopics();
        for (String focusTopic : focusTopics) {
            if (answeredQuestions.stream().anyMatch(question -> matchesFocus(question, focusTopic))) {
                covered.add(focusTopic);
            } else {
                unverified.add(focusTopic);
            }
        }
        if (requirementTargets.isEmpty() && focusTopics.isEmpty()) {
            answeredQuestions.stream()
                .map(InterviewQuestionDTO::type)
                .filter(type -> type != null && !type.isBlank())
                .forEach(covered::add);
        }
        boolean partialReason = Set.of(
            EndReason.USER_REQUESTED,
            EndReason.MANUAL_BUTTON,
            EndReason.LOW_INFORMATION,
            EndReason.OFF_TOPIC,
            EndReason.SYSTEM_ERROR
        ).contains(endReason);
        CompletionType completionType = partialReason || !unverified.isEmpty()
            ? CompletionType.PARTIAL
            : CompletionType.COMPLETE;
        persistenceService.completeSession(
            sessionId,
            endReason,
            completionType,
            List.copyOf(covered),
            List.copyOf(unverified)
        );
    }

    /** 所有自然结束、用户结束和手动结束统一经过此处，确保重复请求不会重复入队。 */
    private void finishInterview(
            String sessionId,
            List<InterviewQuestionDTO> questions,
            int finalIndex,
            EndReason endReason) {
        sessionCache.updateCurrentIndex(sessionId, finalIndex);
        sessionCache.updateSessionStatus(sessionId, SessionStatus.COMPLETED);
        persistenceService.updateCurrentQuestionIndex(sessionId, finalIndex);
        completeSession(sessionId, questions, endReason);
        boolean hasAnswers = questions.stream()
            .anyMatch(question -> question.userAnswer() != null && !question.userAnswer().isBlank());
        if (hasAnswers) {
            enqueueEvaluationTask(sessionId);
        }
    }

    private boolean matchesFocus(InterviewQuestionDTO question, String focusTopic) {
        if (focusTopic == null || focusTopic.isBlank()) {
            return false;
        }
        String normalizedFocus = focusTopic.toLowerCase();
        return java.util.stream.Stream.of(
                question.type(),
                question.category(),
                question.topicSummary(),
                question.question())
            .filter(value -> value != null && !value.isBlank())
            .anyMatch(value -> value.toLowerCase().contains(normalizedFocus)
                || normalizedFocus.contains(value.toLowerCase()));
    }

    private EndReason resolveEndReason(String value) {
        if (value == null || value.isBlank()) {
            return EndReason.SUFFICIENT_EVIDENCE;
        }
        try {
            EndReason reason = EndReason.valueOf(value.trim().toUpperCase());
            return reason == EndReason.USER_REQUESTED || reason == EndReason.MANUAL_BUTTON
                ? EndReason.SUFFICIENT_EVIDENCE
                : reason;
        } catch (IllegalArgumentException e) {
            return EndReason.SUFFICIENT_EVIDENCE;
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private InterviewBlueprintDTO normalizeBlueprint(CreateInterviewRequest request) {
        InterviewBlueprintDTO source = request.blueprint();
        String defaultMode = request.jobId() != null || request.matchReportId() != null
            ? "JOB_TARGETED"
            : request.resumeId() != null ? "RESUME_DEFENSE" : "GENERAL";
        String mode = source != null && BLUEPRINT_MODES.contains(source.mode())
            ? source.mode() : defaultMode;
        String difficulty = source != null && DIFFICULTIES.contains(source.difficulty())
            ? source.difficulty()
            : DIFFICULTIES.contains(request.difficulty())
                ? request.difficulty() : InterviewDefaults.DIFFICULTY;
        int questionCount = Math.max(3, Math.min(request.questionCount(), 20));
        int maxFollowUps = source == null
            ? 2 : Math.max(0, Math.min(source.maxFollowUpsPerTopic(), 2));

        List<String> validRequirementIds = List.of();
        if (request.matchReportId() != null) {
            Set<String> allowedIds = jobMatchService.getReport(request.matchReportId())
                .evidenceMappings().stream()
                .map(mapping -> mapping.requirement().id())
                .collect(java.util.stream.Collectors.toSet());
            validRequirementIds = sanitizeList(
                source == null ? List.of() : source.targetRequirementIds(), 8, 40).stream()
                .filter(allowedIds::contains)
                .toList();
        }

        List<String> focusTopics = sanitizeList(
            source == null ? List.of() : source.focusTopics(), 8, 80);
        if (focusTopics.isEmpty() && request.customCategories() != null) {
            focusTopics = request.customCategories().stream()
                .map(category -> category.label())
                .filter(label -> label != null && !label.isBlank())
                .limit(8)
                .toList();
        }
        List<String> questionTypes = sanitizeList(
            source == null ? List.of() : source.questionTypes(), 4, 40).stream()
            .map(String::toUpperCase)
            .filter(QUESTION_TYPES::contains)
            .toList();
        if (questionTypes.isEmpty()) {
            questionTypes = List.of("CONCEPT", "PROJECT_EVIDENCE", "SCENARIO_DESIGN", "TROUBLESHOOTING");
        }

        String objective = normalizeText(
            source == null ? null : source.objective(),
            mode.equals("FOCUS_DRILL") ? "按用户指定方向进行专项强化" : "验证岗位要求与简历能力证据",
            300
        );
        String rationale = normalizeText(
            source == null ? null : source.rationale(),
            "根据当前面试方向、简历与岗位上下文生成问题。",
            500
        );
        return new InterviewBlueprintDTO(
            mode,
            objective,
            validRequirementIds,
            focusTopics,
            questionTypes,
            sanitizeList(source == null ? List.of() : source.avoidTopics(), 8, 80),
            difficulty,
            questionCount,
            maxFollowUps,
            rationale
        );
    }

    private List<String> sanitizeList(List<String> values, int maxItems, int maxLength) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String normalized = value.trim();
            result.add(normalized.substring(0, Math.min(normalized.length(), maxLength)));
            if (result.size() >= maxItems) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private String normalizeText(String value, String fallback, int maxLength) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        return normalized.substring(0, Math.min(normalized.length(), maxLength));
    }
}
