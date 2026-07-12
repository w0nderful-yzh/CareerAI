package com.yzh666.careerai.modules.jobmatch.service;

import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.common.model.AsyncTaskStatus;
import com.yzh666.careerai.modules.aitask.model.AiAnalysisTaskEntity;
import com.yzh666.careerai.modules.aitask.model.AiAnalysisTaskType;
import com.yzh666.careerai.modules.aitask.repository.AiAnalysisTaskRepository;
import com.yzh666.careerai.modules.job.repository.JobRepository;
import com.yzh666.careerai.modules.jobmatch.dto.CreateJobMatchRequest;
import com.yzh666.careerai.modules.jobmatch.dto.JobMatchReportDTO;
import com.yzh666.careerai.modules.jobmatch.dto.JobMatchTaskDTO;
import com.yzh666.careerai.modules.jobmatch.messaging.JobMatchRabbitProducer;
import com.yzh666.careerai.modules.jobmatch.messaging.JobMatchTaskMessage;
import com.yzh666.careerai.modules.jobmatch.repository.JobMatchReportRepository;
import com.yzh666.careerai.modules.resume.service.ResumePersistenceService;
import com.yzh666.careerai.modules.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobMatchTaskService {

  private final AiAnalysisTaskRepository taskRepository;
  private final JobMatchReportRepository reportRepository;
  private final JobRepository jobRepository;
  private final ResumePersistenceService resumePersistenceService;
  private final CurrentUserService currentUserService;
  private final JobMatchService jobMatchService;
  private final ObjectProvider<JobMatchRabbitProducer> producerProvider;

  public JobMatchTaskDTO createTask(CreateJobMatchRequest request) {
    Long userId = currentUserService.currentUserId();
    resumePersistenceService.findByIdAndUserId(request.resumeId(), userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));
    jobRepository.findByIdAndUserId(request.jobId(), userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));

    AiAnalysisTaskEntity task = new AiAnalysisTaskEntity();
    task.setUserId(userId);
    task.setTaskType(AiAnalysisTaskType.JOB_MATCH);
    task.setStatus(AsyncTaskStatus.PENDING);
    task.setBizId(request.jobId());
    task.setResumeId(request.resumeId());
    task.setJobId(request.jobId());
    task.setRetryCount(0);
    AiAnalysisTaskEntity saved = taskRepository.save(task);

    JobMatchRabbitProducer producer = producerProvider.getIfAvailable();
    if (producer == null) {
      saved.setStatus(AsyncTaskStatus.PROCESSING);
      taskRepository.save(saved);
      JobMatchReportDTO report = jobMatchService.createReportForUser(
          userId,
          request.resumeId(),
          request.jobId()
      );
      saved.setStatus(AsyncTaskStatus.COMPLETED);
      saved.setResultId(report.id());
      saved.setErrorMessage("RabbitMQ 未启用，已使用同步兜底生成报告");
      return toDTO(saved);
    }

    producer.send(new JobMatchTaskMessage(
        saved.getId(),
        saved.getUserId(),
        saved.getResumeId(),
        saved.getJobId(),
        0
    ));
    return toDTO(saved);
  }

  public JobMatchTaskDTO getTask(Long taskId) {
    Long userId = currentUserService.currentUserId();
    AiAnalysisTaskEntity task = taskRepository.findByIdAndUserId(taskId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "任务不存在"));
    return toDTO(task);
  }

  JobMatchTaskDTO toDTO(AiAnalysisTaskEntity task) {
    return new JobMatchTaskDTO(
        task.getId(),
        task.getStatus(),
        task.getResumeId(),
        task.getJobId(),
        task.getResultId(),
        task.getRetryCount(),
        task.getErrorMessage(),
        loadReport(task),
        task.getCreatedAt(),
        task.getUpdatedAt()
    );
  }

  private com.yzh666.careerai.modules.jobmatch.dto.JobMatchReportDTO loadReport(AiAnalysisTaskEntity task) {
    if (task.getResultId() == null) {
      return null;
    }
    return reportRepository.findByIdAndUserId(task.getResultId(), task.getUserId())
        .map(jobMatchService::toDTO)
        .orElse(null);
  }
}
