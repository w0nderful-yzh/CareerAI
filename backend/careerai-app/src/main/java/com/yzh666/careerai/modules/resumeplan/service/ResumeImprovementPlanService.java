package com.yzh666.careerai.modules.resumeplan.service;

import com.yzh666.careerai.common.ai.LlmProviderRegistry;
import com.yzh666.careerai.common.ai.StructuredOutputInvoker;
import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.modules.interview.model.InterviewSessionEntity;
import com.yzh666.careerai.modules.interview.repository.InterviewSessionRepository;
import com.yzh666.careerai.modules.job.model.JobEntity;
import com.yzh666.careerai.modules.job.repository.JobRepository;
import com.yzh666.careerai.modules.jobmatch.model.JobMatchReportEntity;
import com.yzh666.careerai.modules.jobmatch.repository.JobMatchReportRepository;
import com.yzh666.careerai.modules.resume.model.ResumeEntity;
import com.yzh666.careerai.modules.resume.repository.ResumeRepository;
import com.yzh666.careerai.modules.resumeplan.dto.CreateResumeImprovementPlanRequest;
import com.yzh666.careerai.modules.resumeplan.dto.ResumeImprovementPlanDTO;
import com.yzh666.careerai.modules.resumeplan.model.ResumeImprovementPlanEntity;
import com.yzh666.careerai.modules.resumeplan.repository.ResumeImprovementPlanRepository;
import com.yzh666.careerai.modules.user.service.CurrentUserService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class ResumeImprovementPlanService {

    private static final int MAX_RESUME_TEXT_LENGTH = 14_000;
    private static final int MAX_JD_TEXT_LENGTH = 10_000;
    private static final int MAX_INTERVIEW_REVIEW_LENGTH = 8_000;

    private final ResumeImprovementPlanRepository planRepository;
    private final JobMatchReportRepository matchReportRepository;
    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final CurrentUserService currentUserService;
    private final LlmProviderRegistry llmProviderRegistry;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final ObjectMapper objectMapper;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final BeanOutputConverter<PlanAnalysisDTO> outputConverter;

    private record PlanAnalysisDTO(
        Integer readinessScore,
        String summary,
        List<String> priorityFixes,
        List<String> resumeRewriteBullets,
        List<String> projectUpgradeTasks,
        List<String> interviewPracticeTasks,
        List<String> learningTasks
    ) {
    }

    public ResumeImprovementPlanService(
        ResumeImprovementPlanRepository planRepository,
        JobMatchReportRepository matchReportRepository,
        ResumeRepository resumeRepository,
        JobRepository jobRepository,
        InterviewSessionRepository interviewSessionRepository,
        CurrentUserService currentUserService,
        LlmProviderRegistry llmProviderRegistry,
        StructuredOutputInvoker structuredOutputInvoker,
        ObjectMapper objectMapper,
        ResourceLoader resourceLoader
    ) throws IOException {
        this.planRepository = planRepository;
        this.matchReportRepository = matchReportRepository;
        this.resumeRepository = resumeRepository;
        this.jobRepository = jobRepository;
        this.interviewSessionRepository = interviewSessionRepository;
        this.currentUserService = currentUserService;
        this.llmProviderRegistry = llmProviderRegistry;
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.objectMapper = objectMapper;
        this.systemPromptTemplate = new PromptTemplate(
            resourceLoader.getResource("classpath:prompts/resume-improvement-plan-system.st")
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.userPromptTemplate = new PromptTemplate(
            resourceLoader.getResource("classpath:prompts/resume-improvement-plan-user.st")
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.outputConverter = new BeanOutputConverter<>(PlanAnalysisDTO.class);
    }

    public List<ResumeImprovementPlanDTO> listPlans(Long matchReportId) {
        Long userId = currentUserService.currentUserId();
        List<ResumeImprovementPlanEntity> plans = matchReportId == null
            ? planRepository.findByUserIdOrderByCreatedAtDesc(userId)
            : planRepository.findByUserIdAndMatchReportIdOrderByCreatedAtDesc(userId, matchReportId);
        return plans.stream().map(this::toDTO).toList();
    }

    public ResumeImprovementPlanDTO getPlan(Long id) {
        Long userId = currentUserService.currentUserId();
        ResumeImprovementPlanEntity plan = planRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_PLAN_NOT_FOUND));
        return toDTO(plan);
    }

    public ResumeImprovementPlanDTO createPlan(CreateResumeImprovementPlanRequest request) {
        Long userId = currentUserService.currentUserId();
        return createPlan(request, userId, null);
    }

    public ResumeImprovementPlanDTO createPlanIdempotently(
        CreateResumeImprovementPlanRequest request,
        String idempotencyKey
    ) {
        Long userId = currentUserService.currentUserId();
        return planRepository.findByUserIdAndAgentIdempotencyKey(userId, idempotencyKey)
            .map(this::toDTO)
            .orElseGet(() -> createPlan(request, userId, idempotencyKey));
    }

    private ResumeImprovementPlanDTO createPlan(
        CreateResumeImprovementPlanRequest request,
        Long userId,
        String idempotencyKey
    ) {
        JobMatchReportEntity report = matchReportRepository.findByIdAndUserId(request.matchReportId(), userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.JOB_MATCH_NOT_FOUND));
        ResumeEntity resume = resumeRepository.findByIdAndUserId(report.getResumeId(), userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));
        JobEntity job = jobRepository.findByIdAndUserId(report.getJobId(), userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));

        if (resume.getResumeText() == null || resume.getResumeText().isBlank()) {
            throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED, "简历文本为空，请重新上传或解析简历");
        }

        PlanAnalysisDTO analysis = generatePlan(resume, job, report, findLatestJobEvaluation(userId, report));
        ResumeImprovementPlanEntity entity = new ResumeImprovementPlanEntity();
        entity.setUserId(userId);
        entity.setMatchReportId(report.getId());
        entity.setResumeId(resume.getId());
        entity.setResumeFilename(resume.getOriginalFilename());
        entity.setJobId(job.getId());
        entity.setJobTitle(job.getTitle());
        entity.setReadinessScore(clampScore(analysis.readinessScore()));
        entity.setSummary(defaultText(analysis.summary(), "已生成面向目标岗位的简历改进计划。"));
        entity.setPriorityFixesJson(writeList(analysis.priorityFixes()));
        entity.setResumeRewriteBulletsJson(writeList(analysis.resumeRewriteBullets()));
        entity.setProjectUpgradeTasksJson(writeList(analysis.projectUpgradeTasks()));
        entity.setInterviewPracticeTasksJson(writeList(analysis.interviewPracticeTasks()));
        entity.setLearningTasksJson(writeList(analysis.learningTasks()));
        entity.setAgentIdempotencyKey(idempotencyKey);

        ResumeImprovementPlanEntity saved;
        try {
            // LLM 调用发生在事务外；最终写入再用唯一约束收敛并发重试。
            saved = planRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            if (idempotencyKey == null) {
                throw exception;
            }
            return planRepository.findByUserIdAndAgentIdempotencyKey(userId, idempotencyKey)
                .map(this::toDTO)
                .orElseThrow(() -> exception);
        }
        log.info(
            "简历改进计划已生成: planId={}, userId={}, matchReportId={}, resumeId={}, jobId={}",
            saved.getId(),
            userId,
            report.getId(),
            resume.getId(),
            job.getId()
        );
        return toDTO(saved);
    }

    private PlanAnalysisDTO generatePlan(
        ResumeEntity resume,
        JobEntity job,
        JobMatchReportEntity report,
        String latestInterviewReview
    ) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("resumeFilename", resume.getOriginalFilename());
            variables.put("resumeText", truncate(resume.getResumeText(), MAX_RESUME_TEXT_LENGTH));
            variables.put("jobTitle", job.getTitle());
            variables.put("company", defaultText(job.getCompany(), "未填写"));
            variables.put("location", defaultText(job.getLocation(), "未填写"));
            variables.put("jdText", truncate(job.getJdText(), MAX_JD_TEXT_LENGTH));
            variables.put("matchReport", buildMatchReportContext(report));
            variables.put(
                "latestInterviewReview",
                latestInterviewReview.isBlank() ? "暂无岗位模拟面试复盘。" : latestInterviewReview
            );

            String systemPrompt = systemPromptTemplate.render() + "\n\n" + outputConverter.getFormat();
            String userPrompt = userPromptTemplate.render(variables);
            ChatClient chatClient = llmProviderRegistry.getDefaultChatClient();
            return structuredOutputInvoker.invoke(
                chatClient,
                systemPrompt,
                userPrompt,
                outputConverter,
                ErrorCode.RESUME_PLAN_FAILED,
                "简历改进计划生成失败：",
                "简历改进计划",
                log
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("简历改进计划生成失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.RESUME_PLAN_FAILED, "简历改进计划生成失败：" + e.getMessage());
        }
    }

    private String findLatestJobEvaluation(Long userId, JobMatchReportEntity report) {
        return interviewSessionRepository.findByUserIdAndJobIdOrderByCreatedAtDesc(userId, report.getJobId()).stream()
            .filter(session -> report.getResumeId().equals(session.getResumeId()))
            .filter(session -> session.getJobEvaluationJson() != null && !session.getJobEvaluationJson().isBlank())
            .findFirst()
            .map(this::buildInterviewReviewContext)
            .orElse("");
    }

    private String buildInterviewReviewContext(InterviewSessionEntity session) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 最近一次岗位模拟面试\n");
        sb.append("- 面试分数：").append(session.getOverallScore() == null ? "未评分" : session.getOverallScore()).append("/100\n");
        sb.append("- 总体反馈：").append(defaultText(session.getOverallFeedback(), "暂无")).append("\n");
        sb.append("- 岗位复盘 JSON：\n");
        sb.append(truncate(session.getJobEvaluationJson(), MAX_INTERVIEW_REVIEW_LENGTH));
        return sb.toString();
    }

    private String buildMatchReportContext(JobMatchReportEntity report) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 简历-岗位匹配报告\n");
        sb.append("- 岗位：").append(report.getJobTitle()).append('\n');
        sb.append("- 简历：").append(report.getResumeFilename()).append('\n');
        sb.append("- 总体匹配度：").append(report.getOverallScore()).append("/100\n");
        sb.append("- 技能匹配：").append(report.getSkillScore()).append("/100\n");
        sb.append("- 项目支撑：").append(report.getProjectScore()).append("/100\n");
        sb.append("- 关键词覆盖：").append(report.getKeywordScore()).append("/100\n");
        sb.append("- 匹配结论：").append(report.getSummary()).append("\n\n");
        appendList(sb, "匹配亮点", readList(report.getMatchedHighlightsJson()));
        appendList(sb, "主要差距", readList(report.getGapsJson()));
        appendList(sb, "行动项", readList(report.getActionItemsJson()));
        return sb.toString();
    }

    private ResumeImprovementPlanDTO toDTO(ResumeImprovementPlanEntity entity) {
        return new ResumeImprovementPlanDTO(
            entity.getId(),
            entity.getMatchReportId(),
            entity.getResumeId(),
            entity.getResumeFilename(),
            entity.getJobId(),
            entity.getJobTitle(),
            entity.getReadinessScore(),
            entity.getSummary(),
            readList(entity.getPriorityFixesJson()),
            readList(entity.getResumeRewriteBulletsJson()),
            readList(entity.getProjectUpgradeTasksJson()),
            readList(entity.getInterviewPracticeTasksJson()),
            readList(entity.getLearningTasksJson()),
            entity.getCreatedAt()
        );
    }

    private String writeList(List<String> items) {
        try {
            List<String> safeItems = items == null ? List.of() : items.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .limit(8)
                .toList();
            return objectMapper.writeValueAsString(safeItems);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.RESUME_PLAN_FAILED, "保存简历改进计划失败");
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JacksonException e) {
            log.warn("读取简历改进计划列表字段失败: {}", e.getMessage());
            return List.of();
        }
    }

    private void appendList(StringBuilder sb, String title, List<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        sb.append("### ").append(title).append('\n');
        for (String item : items.stream().limit(6).toList()) {
            sb.append("- ").append(item).append('\n');
        }
        sb.append('\n');
    }

    private int clampScore(Integer score) {
        if (score == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, score));
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength) + "\n[内容过长，已截断]";
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
