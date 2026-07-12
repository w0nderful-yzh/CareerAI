package com.yzh666.careerai.modules.jobmatch.service;

import com.yzh666.careerai.common.ai.LlmProviderRegistry;
import com.yzh666.careerai.common.ai.StructuredOutputInvoker;
import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.modules.job.model.JobEntity;
import com.yzh666.careerai.modules.job.repository.JobRepository;
import com.yzh666.careerai.modules.jobmatch.dto.CreateJobMatchRequest;
import com.yzh666.careerai.modules.jobmatch.dto.JobMatchReportDTO;
import com.yzh666.careerai.modules.jobmatch.model.JobMatchReportEntity;
import com.yzh666.careerai.modules.jobmatch.repository.JobMatchReportRepository;
import com.yzh666.careerai.modules.resume.model.ResumeEntity;
import com.yzh666.careerai.modules.resume.service.ResumePersistenceService;
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
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class JobMatchService {

    private static final int MAX_RESUME_TEXT_LENGTH = 16_000;
    private static final int MAX_JD_TEXT_LENGTH = 12_000;

    private final JobMatchReportRepository reportRepository;
    private final JobRepository jobRepository;
    private final ResumePersistenceService resumePersistenceService;
    private final CurrentUserService currentUserService;
    private final LlmProviderRegistry llmProviderRegistry;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final ObjectMapper objectMapper;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final BeanOutputConverter<JobMatchAnalysisDTO> outputConverter;

    private record JobMatchAnalysisDTO(
        Integer overallScore,
        Integer skillScore,
        Integer projectScore,
        Integer keywordScore,
        String summary,
        List<String> matchedHighlights,
        List<String> gaps,
        List<String> actionItems
    ) {
    }

    public JobMatchService(
        JobMatchReportRepository reportRepository,
        JobRepository jobRepository,
        ResumePersistenceService resumePersistenceService,
        CurrentUserService currentUserService,
        LlmProviderRegistry llmProviderRegistry,
        StructuredOutputInvoker structuredOutputInvoker,
        ObjectMapper objectMapper,
        ResourceLoader resourceLoader
    ) throws IOException {
        this.reportRepository = reportRepository;
        this.jobRepository = jobRepository;
        this.resumePersistenceService = resumePersistenceService;
        this.currentUserService = currentUserService;
        this.llmProviderRegistry = llmProviderRegistry;
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.objectMapper = objectMapper;
        this.systemPromptTemplate = new PromptTemplate(
            resourceLoader.getResource("classpath:prompts/job-match-system.st")
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.userPromptTemplate = new PromptTemplate(
            resourceLoader.getResource("classpath:prompts/job-match-user.st")
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.outputConverter = new BeanOutputConverter<>(JobMatchAnalysisDTO.class);
    }

    public JobMatchReportDTO createReport(CreateJobMatchRequest request) {
        Long userId = currentUserService.currentUserId();
        return createReportForUser(userId, request.resumeId(), request.jobId());
    }

    public JobMatchReportDTO createReportForUser(Long userId, Long resumeId, Long jobId) {
        ResumeEntity resume = resumePersistenceService.findByIdAndUserId(resumeId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));
        JobEntity job = jobRepository.findByIdAndUserId(jobId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));

        if (resume.getResumeText() == null || resume.getResumeText().isBlank()) {
            throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED, "简历文本为空，请重新上传或解析简历");
        }

        JobMatchAnalysisDTO analysis = analyzeMatch(resume, job);
        JobMatchReportEntity entity = new JobMatchReportEntity();
        entity.setUserId(userId);
        entity.setResumeId(resume.getId());
        entity.setJobId(job.getId());
        entity.setResumeFilename(resume.getOriginalFilename());
        entity.setJobTitle(job.getTitle());
        entity.setOverallScore(clampScore(analysis.overallScore()));
        entity.setSkillScore(clampScore(analysis.skillScore()));
        entity.setProjectScore(clampScore(analysis.projectScore()));
        entity.setKeywordScore(clampScore(analysis.keywordScore()));
        entity.setSummary(defaultText(analysis.summary(), "已生成简历与岗位匹配报告。"));
        entity.setMatchedHighlightsJson(writeList(analysis.matchedHighlights()));
        entity.setGapsJson(writeList(analysis.gaps()));
        entity.setActionItemsJson(writeList(analysis.actionItems()));

        JobMatchReportEntity saved = reportRepository.save(entity);
        log.info(
            "岗位匹配报告已生成: reportId={}, userId={}, resumeId={}, jobId={}, score={}",
            saved.getId(),
            userId,
            resume.getId(),
            job.getId(),
            saved.getOverallScore()
        );
        return toDTO(saved);
    }

    public List<JobMatchReportDTO> listReports(Long jobId) {
        Long userId = currentUserService.currentUserId();
        List<JobMatchReportEntity> reports = jobId == null
            ? reportRepository.findByUserIdOrderByCreatedAtDesc(userId)
            : reportRepository.findByUserIdAndJobIdOrderByCreatedAtDesc(userId, jobId);
        return reports.stream().map(this::toDTO).toList();
    }

    public String buildInterviewContext(Long matchReportId, Long jobId, Long resumeId) {
        Long userId = currentUserService.currentUserId();
        JobMatchReportEntity report = loadReportForInterview(userId, matchReportId, jobId, resumeId);
        if (report == null) {
            return "";
        }

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

    private JobMatchAnalysisDTO analyzeMatch(ResumeEntity resume, JobEntity job) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("resumeFilename", resume.getOriginalFilename());
            variables.put("resumeText", truncate(resume.getResumeText(), MAX_RESUME_TEXT_LENGTH));
            variables.put("jobTitle", job.getTitle());
            variables.put("company", defaultText(job.getCompany(), "未填写"));
            variables.put("location", defaultText(job.getLocation(), "未填写"));
            variables.put("jdText", truncate(job.getJdText(), MAX_JD_TEXT_LENGTH));
            variables.put("parsedCategories", defaultText(job.getParsedCategoriesJson(), "[]"));

            String systemPrompt = systemPromptTemplate.render() + "\n\n" + outputConverter.getFormat();
            String userPrompt = userPromptTemplate.render(variables);
            ChatClient chatClient = llmProviderRegistry.getDefaultChatClient();
            return structuredOutputInvoker.invoke(
                chatClient,
                systemPrompt,
                userPrompt,
                outputConverter,
                ErrorCode.JOB_MATCH_FAILED,
                "简历岗位匹配失败：",
                "简历岗位匹配",
                log
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("简历岗位匹配失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.JOB_MATCH_FAILED, "简历岗位匹配失败：" + e.getMessage());
        }
    }

    private JobMatchReportEntity loadReportForInterview(
        Long userId,
        Long matchReportId,
        Long jobId,
        Long resumeId
    ) {
        if (matchReportId != null) {
            return reportRepository.findByIdAndUserId(matchReportId, userId).orElse(null);
        }
        if (jobId != null && resumeId != null) {
            return reportRepository.findFirstByUserIdAndJobIdAndResumeIdOrderByCreatedAtDesc(
                userId,
                jobId,
                resumeId
            ).orElse(null);
        }
        if (jobId != null) {
            return reportRepository.findFirstByUserIdAndJobIdOrderByCreatedAtDesc(userId, jobId).orElse(null);
        }
        return null;
    }

    JobMatchReportDTO toDTO(JobMatchReportEntity entity) {
        return new JobMatchReportDTO(
            entity.getId(),
            entity.getResumeId(),
            entity.getResumeFilename(),
            entity.getJobId(),
            entity.getJobTitle(),
            entity.getOverallScore(),
            entity.getSkillScore(),
            entity.getProjectScore(),
            entity.getKeywordScore(),
            entity.getSummary(),
            readList(entity.getMatchedHighlightsJson()),
            readList(entity.getGapsJson()),
            readList(entity.getActionItemsJson()),
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
            throw new BusinessException(ErrorCode.JOB_MATCH_FAILED, "保存匹配报告失败");
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
            log.warn("读取岗位匹配报告列表字段失败: {}", e.getMessage());
            return List.of();
        }
    }

    private void appendList(StringBuilder sb, String title, List<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        sb.append("### ").append(title).append('\n');
        for (String item : items.stream().limit(5).toList()) {
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
            return text;
        }
        return text.substring(0, maxLength) + "\n[内容过长，已截断]";
    }

    private String defaultText(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text;
    }
}
