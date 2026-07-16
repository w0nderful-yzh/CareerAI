package com.yzh666.careerai.modules.agenttool.service;

import com.yzh666.careerai.common.agent.tool.AgentJobMatchCommand;
import com.yzh666.careerai.common.agent.tool.AgentJobMatchReport;
import com.yzh666.careerai.common.agent.tool.AgentJobMatchTask;
import com.yzh666.careerai.common.agent.tool.AgentJobSnapshot;
import com.yzh666.careerai.common.agent.tool.AgentResumeDetail;
import com.yzh666.careerai.common.agent.tool.AgentResumeImprovementPlan;
import com.yzh666.careerai.common.agent.tool.AgentResumeImprovementPlanCommand;
import com.yzh666.careerai.common.agent.tool.AgentResumeSummary;
import com.yzh666.careerai.modules.job.dto.JobDTO;
import com.yzh666.careerai.modules.job.service.JobService;
import com.yzh666.careerai.modules.jobmatch.dto.CreateJobMatchRequest;
import com.yzh666.careerai.modules.jobmatch.dto.JobMatchReportDTO;
import com.yzh666.careerai.modules.jobmatch.dto.JobMatchTaskDTO;
import com.yzh666.careerai.modules.jobmatch.service.JobMatchService;
import com.yzh666.careerai.modules.jobmatch.service.JobMatchTaskService;
import com.yzh666.careerai.modules.resume.model.ResumeDetailDTO;
import com.yzh666.careerai.modules.resume.model.ResumeListItemDTO;
import com.yzh666.careerai.modules.resume.service.ResumeHistoryService;
import com.yzh666.careerai.modules.resumeplan.dto.CreateResumeImprovementPlanRequest;
import com.yzh666.careerai.modules.resumeplan.dto.ResumeImprovementPlanDTO;
import com.yzh666.careerai.modules.resumeplan.service.ResumeImprovementPlanService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 把现有业务 Service 适配成稳定的 Agent Tool 契约。
 * 这里不绕过领域服务，因此用户隔离、业务校验和异常语义仍保持一致。
 */
@Service
@RequiredArgsConstructor
public class AgentBusinessToolService {

  private final ResumeHistoryService resumeHistoryService;
  private final JobService jobService;
  private final JobMatchTaskService jobMatchTaskService;
  private final JobMatchService jobMatchService;
  private final ResumeImprovementPlanService improvementPlanService;

  public List<AgentResumeSummary> listResumes() {
    return resumeHistoryService.getAllResumes().stream()
        .map(this::toResumeSummary)
        .toList();
  }

  public AgentResumeDetail getResumeDetail(Long resumeId) {
    ResumeDetailDTO detail = resumeHistoryService.getResumeDetail(resumeId);
    ResumeDetailDTO.AnalysisHistoryDTO latest = detail.analyses().isEmpty()
        ? null
        : detail.analyses().getFirst();
    return new AgentResumeDetail(
        detail.id(),
        detail.filename(),
        detail.resumeText(),
        enumName(detail.analyzeStatus()),
        latest == null ? null : latest.overallScore(),
        latest == null ? null : latest.summary(),
        latest == null ? List.of() : latest.strengths()
    );
  }

  public AgentJobSnapshot getJob(Long jobId) {
    JobDTO job = jobService.getJob(jobId);
    return new AgentJobSnapshot(
        job.id(),
        job.title(),
        job.company(),
        job.location(),
        enumName(job.status()),
        job.jdText(),
        job.updatedAt()
    );
  }

  public AgentJobMatchTask startJobMatch(
      AgentJobMatchCommand command,
      String idempotencyKey
  ) {
    return toJobMatchTask(jobMatchTaskService.createTaskIdempotently(
        new CreateJobMatchRequest(command.resumeId(), command.jobId()),
        idempotencyKey
    ));
  }

  public AgentJobMatchTask getJobMatchTask(Long taskId) {
    return toJobMatchTask(jobMatchTaskService.getTask(taskId));
  }

  public AgentJobMatchReport getMatchReport(Long reportId) {
    return toJobMatchReport(jobMatchService.getReport(reportId));
  }

  public AgentResumeImprovementPlan createImprovementPlan(
      AgentResumeImprovementPlanCommand command,
      String idempotencyKey
  ) {
    return toImprovementPlan(improvementPlanService.createPlanIdempotently(
        new CreateResumeImprovementPlanRequest(command.matchReportId()),
        idempotencyKey
    ));
  }

  public AgentResumeImprovementPlan getImprovementPlan(Long planId) {
    return toImprovementPlan(improvementPlanService.getPlan(planId));
  }

  private AgentResumeSummary toResumeSummary(ResumeListItemDTO resume) {
    return new AgentResumeSummary(
        resume.id(),
        resume.filename(),
        resume.latestScore(),
        enumName(resume.analyzeStatus()),
        resume.uploadedAt()
    );
  }

  private AgentJobMatchTask toJobMatchTask(JobMatchTaskDTO task) {
    return new AgentJobMatchTask(
        task.id(),
        enumName(task.status()),
        task.resumeId(),
        task.jobId(),
        task.reportId(),
        task.errorMessage(),
        task.report() == null ? null : toJobMatchReport(task.report()),
        task.createdAt(),
        task.updatedAt()
    );
  }

  private AgentJobMatchReport toJobMatchReport(JobMatchReportDTO report) {
    return new AgentJobMatchReport(
        report.id(),
        report.resumeId(),
        report.resumeFilename(),
        report.jobId(),
        report.jobTitle(),
        report.overallScore(),
        report.skillScore(),
        report.projectScore(),
        report.keywordScore(),
        report.summary(),
        report.matchedHighlights(),
        report.gaps(),
        report.actionItems(),
        report.createdAt()
    );
  }

  private AgentResumeImprovementPlan toImprovementPlan(ResumeImprovementPlanDTO plan) {
    return new AgentResumeImprovementPlan(
        plan.id(),
        plan.matchReportId(),
        plan.resumeId(),
        plan.resumeFilename(),
        plan.jobId(),
        plan.jobTitle(),
        plan.readinessScore(),
        plan.summary(),
        plan.priorityFixes(),
        plan.resumeRewriteBullets(),
        plan.projectUpgradeTasks(),
        plan.interviewPracticeTasks(),
        plan.learningTasks(),
        plan.createdAt()
    );
  }

  private String enumName(Enum<?> value) {
    return value == null ? null : value.name();
  }
}
