package com.yzh666.careerai.modules.interview.service;

import com.yzh666.careerai.common.evaluation.EvaluationReport;
import com.yzh666.careerai.common.evaluation.QaRecord;
import com.yzh666.careerai.common.evaluation.UnifiedEvaluationService;
import com.yzh666.careerai.common.ai.StructuredOutputInvoker;
import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.modules.interview.model.InterviewQuestionDTO;
import com.yzh666.careerai.modules.interview.model.InterviewReportDTO;
import com.yzh666.careerai.modules.interview.model.InterviewReportDTO.CategoryScore;
import com.yzh666.careerai.modules.interview.model.InterviewReportDTO.DimensionScore;
import com.yzh666.careerai.modules.interview.model.InterviewReportDTO.EvidenceConclusion;
import com.yzh666.careerai.modules.interview.model.InterviewReportDTO.JobEvaluation;
import com.yzh666.careerai.modules.interview.model.InterviewReportDTO.QuestionEvaluation;
import com.yzh666.careerai.modules.interview.model.InterviewReportDTO.ReferenceAnswer;
import com.yzh666.careerai.modules.interview.model.InterviewSessionEntity;
import com.yzh666.careerai.modules.interview.model.TurnEvaluationEvidenceDTO;
import com.yzh666.careerai.modules.interview.repository.InterviewSessionRepository;
import com.yzh666.careerai.modules.interview.skill.InterviewSkillService;
import com.yzh666.careerai.modules.job.model.JobEntity;
import com.yzh666.careerai.modules.job.repository.JobRepository;
import com.yzh666.careerai.modules.jobmatch.model.JobMatchReportEntity;
import com.yzh666.careerai.modules.jobmatch.repository.JobMatchReportRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文字面试答案评估服务
 * 职责：DTO 适配器，将 InterviewQuestionDTO 转为通用 QaRecord，调用 UnifiedEvaluationService
 */
@Service
public class AnswerEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AnswerEvaluationService.class);

    private final UnifiedEvaluationService unifiedEvaluationService;
    private final InterviewPersistenceService persistenceService;
    private final InterviewSkillService skillService;
    private final InterviewSessionRepository sessionRepository;
    private final JobRepository jobRepository;
    private final JobMatchReportRepository matchReportRepository;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final PromptTemplate jobEvaluationSystemPromptTemplate;
    private final PromptTemplate jobEvaluationUserPromptTemplate;
    private final BeanOutputConverter<JobEvaluation> jobEvaluationOutputConverter;

    public AnswerEvaluationService(UnifiedEvaluationService unifiedEvaluationService,
                                   InterviewPersistenceService persistenceService,
                                   InterviewSkillService skillService,
                                   InterviewSessionRepository sessionRepository,
                                   JobRepository jobRepository,
                                   JobMatchReportRepository matchReportRepository,
                                   StructuredOutputInvoker structuredOutputInvoker,
                                   ResourceLoader resourceLoader) throws IOException {
        this.unifiedEvaluationService = unifiedEvaluationService;
        this.persistenceService = persistenceService;
        this.skillService = skillService;
        this.sessionRepository = sessionRepository;
        this.jobRepository = jobRepository;
        this.matchReportRepository = matchReportRepository;
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.jobEvaluationSystemPromptTemplate = new PromptTemplate(
            resourceLoader.getResource("classpath:prompts/interview-job-evaluation-system.st")
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.jobEvaluationUserPromptTemplate = new PromptTemplate(
            resourceLoader.getResource("classpath:prompts/interview-job-evaluation-user.st")
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.jobEvaluationOutputConverter = new BeanOutputConverter<>(JobEvaluation.class);
    }

    /**
     * 评估完整面试并生成报告
     */
    public InterviewReportDTO evaluateInterview(ChatClient chatClient, String sessionId, String resumeText,
                                                 List<InterviewQuestionDTO> questions) {
        log.info("开始评估面试: {}, 共{}题", sessionId, questions.size());

        try {
            // 转为通用问答记录
            List<QaRecord> qaRecords = questions.stream()
                .map(q -> new QaRecord(q.questionIndex(), q.question(), q.category(), q.userAnswer()))
                .toList();

            String referenceContext = skillService.buildEvaluationReferenceSectionSafe(
                persistenceService.findBySessionId(sessionId)
                    .map(s -> s.getSkillId())
                    .orElse(null)
            );

            // 调用通用评估服务
            EvaluationReport report = unifiedEvaluationService.evaluate(
                chatClient, sessionId, qaRecords, resumeText, referenceContext
            );

            // 转为文字面试专用 DTO
            return toInterviewReportDTO(
                report,
                buildJobEvaluation(chatClient, sessionId, resumeText, qaRecords, report)
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("面试评估失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED,
                "面试评估失败：" + e.getMessage());
        }
    }

    private InterviewReportDTO toInterviewReportDTO(EvaluationReport report, JobEvaluation jobEvaluation) {
        var completion = persistenceService.getCompletionSnapshot(report.sessionId());
        var turnEvaluations = persistenceService.getTurnEvaluations(report.sessionId());
        return new InterviewReportDTO(
            report.sessionId(),
            report.totalQuestions(),
            report.overallScore(),
            report.categoryScores().stream()
                .map(cs -> new CategoryScore(cs.category(), cs.score(), cs.questionCount()))
                .toList(),
            report.questionDetails().stream()
                .map(qe -> new QuestionEvaluation(qe.questionIndex(), qe.question(), qe.category(),
                    qe.userAnswer(), qe.score(), qe.feedback()))
                .toList(),
            report.overallFeedback(),
            report.strengths(),
            report.improvements(),
            jobEvaluation,
            report.referenceAnswers().stream()
                .map(ra -> new ReferenceAnswer(ra.questionIndex(), ra.question(),
                    ra.referenceAnswer(), ra.keyPoints()))
                .toList(),
            completion.endReason(),
            completion.completionType(),
            completion.coveredTargets(),
            completion.unverifiedTargets(),
            buildDimensionScores(turnEvaluations),
            buildEvidenceConclusions(turnEvaluations)
        );
    }

    private List<DimensionScore> buildDimensionScores(
            List<TurnEvaluationEvidenceDTO> turns) {
        Map<String, List<Integer>> values = new java.util.LinkedHashMap<>();
        for (var turn : turns) {
            var evaluation = turn.evaluation();
            addDimension(values, "TECHNICAL_CORRECTNESS", evaluation.technicalCorrectness());
            addDimension(values, "TECHNICAL_DEPTH", evaluation.technicalDepth());
            addDimension(values, "COMPLETENESS", evaluation.completeness());
            addDimension(values, "SCENARIO_REASONING", evaluation.scenarioReasoning());
            addDimension(values, "PROJECT_UNDERSTANDING", evaluation.projectUnderstanding());
            addDimension(values, "TROUBLESHOOTING", evaluation.troubleshooting());
            addDimension(values, "EXPRESSION_STRUCTURE", evaluation.expressionStructure());
            addDimension(values, "CLARITY", evaluation.clarity());
            addDimension(values, "CREDIBILITY", evaluation.credibility());
            addDimension(values, "JOB_RELEVANCE", evaluation.jobRelevance());
        }
        return values.entrySet().stream()
            .map(entry -> new DimensionScore(
                entry.getKey(),
                (int) Math.round(entry.getValue().stream()
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0)),
                entry.getValue().size()))
            .toList();
    }

    private List<EvidenceConclusion> buildEvidenceConclusions(
            List<TurnEvaluationEvidenceDTO> turns) {
        List<EvidenceConclusion> result = new ArrayList<>();
        for (var turn : turns) {
            var evaluation = turn.evaluation();
            addEvidence(result, turn, "TECHNICAL_CORRECTNESS", evaluation.technicalCorrectness());
            addEvidence(result, turn, "TECHNICAL_DEPTH", evaluation.technicalDepth());
            addEvidence(result, turn, "COMPLETENESS", evaluation.completeness());
            addEvidence(result, turn, "SCENARIO_REASONING", evaluation.scenarioReasoning());
            addEvidence(result, turn, "PROJECT_UNDERSTANDING", evaluation.projectUnderstanding());
            addEvidence(result, turn, "TROUBLESHOOTING", evaluation.troubleshooting());
            addEvidence(result, turn, "EXPRESSION_STRUCTURE", evaluation.expressionStructure());
            addEvidence(result, turn, "CLARITY", evaluation.clarity());
            addEvidence(result, turn, "CREDIBILITY", evaluation.credibility());
            addEvidence(result, turn, "JOB_RELEVANCE", evaluation.jobRelevance());
        }
        return result;
    }

    private void addDimension(Map<String, List<Integer>> values, String dimension, Integer score) {
        if (score != null) {
            values.computeIfAbsent(dimension, ignored -> new ArrayList<>()).add(score);
        }
    }

    private void addEvidence(
            List<EvidenceConclusion> result,
            TurnEvaluationEvidenceDTO turn,
            String dimension,
            Integer score) {
        if (score == null) {
            return;
        }
        var evaluation = turn.evaluation();
        result.add(new EvidenceConclusion(
            turn.questionIndex(),
            turn.question(),
            dimension,
            score,
            turn.feedback(),
            evaluation.evidenceSnippets(),
            evaluation.missingPoints(),
            evaluation.confidence()
        ));
    }

    private JobEvaluation buildJobEvaluation(
        ChatClient chatClient,
        String sessionId,
        String resumeText,
        List<QaRecord> qaRecords,
        EvaluationReport report
    ) {
        try {
            Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
            if (sessionOpt.isEmpty()) {
                return null;
            }

            InterviewSessionEntity session = sessionOpt.get();
            String jobContext = buildJobContext(session);
            if (jobContext.isBlank()) {
                return null;
            }

            Map<String, Object> variables = new HashMap<>();
            variables.put("resumeText", truncate(resumeText, 3000));
            variables.put("jobContext", jobContext);
            variables.put("qaRecords", buildQaReview(qaRecords, report));
            variables.put("overallFeedback", report.overallFeedback());
            variables.put("strengths", String.join("\n", report.strengths()));
            variables.put("improvements", String.join("\n", report.improvements()));

            String systemPrompt = jobEvaluationSystemPromptTemplate.render()
                + "\n\n" + jobEvaluationOutputConverter.getFormat();
            String userPrompt = jobEvaluationUserPromptTemplate.render(variables);
            return structuredInvoke(chatClient, systemPrompt, userPrompt);
        } catch (Exception e) {
            log.warn("岗位化面试评价生成失败，跳过岗位复盘: sessionId={}, error={}", sessionId, e.getMessage());
            return null;
        }
    }

    private JobEvaluation structuredInvoke(ChatClient chatClient, String systemPrompt, String userPrompt) {
        return structuredOutputInvoker.invoke(
            chatClient,
            systemPrompt,
            userPrompt,
            jobEvaluationOutputConverter,
            ErrorCode.INTERVIEW_EVALUATION_FAILED,
            "岗位化评价失败：",
            "岗位化评价",
            log
        );
    }

    private String buildJobContext(InterviewSessionEntity session) {
        StringBuilder sb = new StringBuilder();
        if (session.getJobId() != null) {
            jobRepository.findById(session.getJobId()).ifPresent(job -> appendJobContext(sb, job));
        }
        if (session.getMatchReportId() != null) {
            matchReportRepository.findById(session.getMatchReportId())
                .ifPresent(report -> appendMatchContext(sb, report));
        }
        return sb.toString();
    }

    private void appendJobContext(StringBuilder sb, JobEntity job) {
        sb.append("## 目标岗位\n");
        sb.append("- 岗位名称：").append(job.getTitle()).append('\n');
        if (job.getCompany() != null) {
            sb.append("- 公司：").append(job.getCompany()).append('\n');
        }
        if (job.getLocation() != null) {
            sb.append("- 地点：").append(job.getLocation()).append('\n');
        }
        sb.append("### JD 原文\n").append(truncate(job.getJdText(), 4000)).append("\n\n");
    }

    private void appendMatchContext(StringBuilder sb, JobMatchReportEntity report) {
        sb.append("## 简历-岗位匹配报告\n");
        sb.append("- 匹配度：").append(report.getOverallScore()).append("/100\n");
        sb.append("- 技能匹配：").append(report.getSkillScore()).append("/100\n");
        sb.append("- 项目支撑：").append(report.getProjectScore()).append("/100\n");
        sb.append("- 关键词覆盖：").append(report.getKeywordScore()).append("/100\n");
        sb.append("- 匹配结论：").append(report.getSummary()).append('\n');
        sb.append("- 匹配亮点：").append(nullToEmpty(report.getMatchedHighlightsJson())).append('\n');
        sb.append("- 主要差距：").append(nullToEmpty(report.getGapsJson())).append('\n');
        sb.append("- 行动项：").append(nullToEmpty(report.getActionItemsJson())).append("\n\n");
    }

    private String buildQaReview(List<QaRecord> qaRecords, EvaluationReport report) {
        StringBuilder sb = new StringBuilder();
        for (var detail : report.questionDetails()) {
            String question = qaRecords.stream()
                .filter(record -> record.questionIndex() == detail.questionIndex())
                .findFirst()
                .map(QaRecord::question)
                .orElse(detail.question());
            sb.append("- Q").append(detail.questionIndex() + 1)
                .append(" [").append(detail.category()).append("] ")
                .append(question).append('\n')
                .append("  - 得分：").append(detail.score()).append('\n')
                .append("  - 回答：").append(nullToEmpty(detail.userAnswer())).append('\n')
                .append("  - 反馈：").append(nullToEmpty(detail.feedback())).append('\n');
        }
        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength) + "\n...(内容过长，已截断)";
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
