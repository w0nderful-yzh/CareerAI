package com.yzh666.careerai.modules.careerreport.service;

import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.infrastructure.export.PdfExportService;
import com.yzh666.careerai.modules.careerreport.dto.CareerReportDTO;
import com.yzh666.careerai.modules.interview.model.InterviewReportDTO;
import com.yzh666.careerai.modules.interview.model.InterviewSessionEntity;
import com.yzh666.careerai.modules.interview.repository.InterviewSessionRepository;
import com.yzh666.careerai.modules.job.model.JobEntity;
import com.yzh666.careerai.modules.job.repository.JobRepository;
import com.yzh666.careerai.modules.jobmatch.model.JobMatchReportEntity;
import com.yzh666.careerai.modules.jobmatch.repository.JobMatchReportRepository;
import com.yzh666.careerai.modules.resume.model.ResumeEntity;
import com.yzh666.careerai.modules.resume.repository.ResumeRepository;
import com.yzh666.careerai.modules.resume.service.ResumeHistoryService.ExportResult;
import com.yzh666.careerai.modules.resumeplan.model.ResumeImprovementPlanEntity;
import com.yzh666.careerai.modules.resumeplan.repository.ResumeImprovementPlanRepository;
import com.yzh666.careerai.modules.user.service.CurrentUserService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class CareerReportService {

    private final JobMatchReportRepository matchReportRepository;
    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final ResumeImprovementPlanRepository planRepository;
    private final CurrentUserService currentUserService;
    private final PdfExportService pdfExportService;
    private final ObjectMapper objectMapper;

    public CareerReportDTO getReport(Long matchReportId) {
        Long userId = currentUserService.currentUserId();
        JobMatchReportEntity matchReport = matchReportRepository.findByIdAndUserId(matchReportId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.JOB_MATCH_NOT_FOUND));
        JobEntity job = jobRepository.findByIdAndUserId(matchReport.getJobId(), userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        ResumeEntity resume = resumeRepository.findByIdAndUserId(matchReport.getResumeId(), userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));

        return new CareerReportDTO(
            matchReport.getId(),
            toJobSnapshot(job),
            new CareerReportDTO.ResumeSnapshot(resume.getId(), resume.getOriginalFilename()),
            toMatchSnapshot(matchReport),
            findLatestInterview(userId, matchReport),
            findLatestPlan(userId, matchReport.getId()),
            LocalDateTime.now()
        );
    }

    public ExportResult exportPdf(Long matchReportId) {
        CareerReportDTO report = getReport(matchReportId);
        try {
            byte[] pdfBytes = pdfExportService.exportCareerReport(report);
            String filename = "求职综合报告_" + report.job().title() + "_" + report.resume().filename() + ".pdf";
            return new ExportResult(pdfBytes, filename);
        } catch (Exception e) {
            log.error("导出求职综合报告失败: matchReportId={}", matchReportId, e);
            throw new BusinessException(ErrorCode.EXPORT_PDF_FAILED, "导出PDF失败: " + e.getMessage());
        }
    }

    private CareerReportDTO.JobSnapshot toJobSnapshot(JobEntity job) {
        return new CareerReportDTO.JobSnapshot(
            job.getId(),
            job.getTitle(),
            job.getCompany(),
            job.getLocation(),
            job.getSourceUrl(),
            job.getJdText()
        );
    }

    private CareerReportDTO.MatchSnapshot toMatchSnapshot(JobMatchReportEntity report) {
        return new CareerReportDTO.MatchSnapshot(
            report.getOverallScore(),
            report.getSkillScore(),
            report.getProjectScore(),
            report.getKeywordScore(),
            report.getSummary(),
            readList(report.getMatchedHighlightsJson()),
            readList(report.getGapsJson()),
            readList(report.getActionItemsJson()),
            report.getCreatedAt()
        );
    }

    private CareerReportDTO.InterviewSnapshot findLatestInterview(Long userId, JobMatchReportEntity report) {
        return interviewSessionRepository.findByUserIdAndJobIdOrderByCreatedAtDesc(userId, report.getJobId()).stream()
            .filter(session -> report.getResumeId().equals(session.getResumeId()))
            .filter(session -> session.getJobEvaluationJson() != null && !session.getJobEvaluationJson().isBlank())
            .findFirst()
            .map(this::toInterviewSnapshot)
            .orElse(null);
    }

    private CareerReportDTO.InterviewSnapshot toInterviewSnapshot(InterviewSessionEntity session) {
        return new CareerReportDTO.InterviewSnapshot(
            session.getSessionId(),
            session.getOverallScore(),
            session.getOverallFeedback(),
            readValue(session.getJobEvaluationJson(), new TypeReference<>() {
            }),
            session.getCompletedAt()
        );
    }

    private CareerReportDTO.PlanSnapshot findLatestPlan(Long userId, Long matchReportId) {
        return planRepository.findFirstByUserIdAndMatchReportIdOrderByCreatedAtDesc(userId, matchReportId)
            .map(this::toPlanSnapshot)
            .orElse(null);
    }

    private CareerReportDTO.PlanSnapshot toPlanSnapshot(ResumeImprovementPlanEntity plan) {
        return new CareerReportDTO.PlanSnapshot(
            plan.getId(),
            plan.getReadinessScore(),
            plan.getSummary(),
            readList(plan.getPriorityFixesJson()),
            readList(plan.getResumeRewriteBulletsJson()),
            readList(plan.getProjectUpgradeTasksJson()),
            readList(plan.getInterviewPracticeTasksJson()),
            readList(plan.getLearningTasksJson()),
            plan.getCreatedAt()
        );
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<String> items = readValue(json, new TypeReference<>() {
        });
        return items == null ? List.of() : items;
    }

    private <T> T readValue(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JacksonException e) {
            log.warn("读取求职综合报告 JSON 字段失败: {}", e.getMessage());
            return null;
        }
    }
}
