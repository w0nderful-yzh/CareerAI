package com.yzh666.careerai.modules.agenttool.controller;

import com.yzh666.careerai.common.agent.AgentInternalAccessService;
import com.yzh666.careerai.common.agent.tool.AgentCreateInterviewSessionCommand;
import com.yzh666.careerai.common.agent.tool.AgentInterviewSession;
import com.yzh666.careerai.common.agent.tool.AgentJobMatchCommand;
import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnCommand;
import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnContext;
import com.yzh666.careerai.common.agent.tool.AgentInterviewPlanningContext;
import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnResult;
import com.yzh666.careerai.common.agent.tool.AgentJobMatchReport;
import com.yzh666.careerai.common.agent.tool.AgentJobMatchTask;
import com.yzh666.careerai.common.agent.tool.AgentJobSnapshot;
import com.yzh666.careerai.common.agent.tool.AgentResumeDetail;
import com.yzh666.careerai.common.agent.tool.AgentResumeImprovementPlan;
import com.yzh666.careerai.common.agent.tool.AgentResumeImprovementPlanCommand;
import com.yzh666.careerai.common.agent.tool.AgentResumeSummary;
import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.common.result.Result;
import com.yzh666.careerai.modules.agenttool.service.AgentBusinessToolService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/agent/tools")
@RequiredArgsConstructor
public class AgentBusinessToolInternalController {

  private final AgentInternalAccessService internalAccessService;
  private final AgentBusinessToolService toolService;

  @GetMapping("/resumes")
  public Result<List<AgentResumeSummary>> listResumes(
      @RequestHeader(value = AgentInternalAccessService.TOKEN_HEADER, required = false) String serviceToken,
      @RequestHeader(value = AgentInternalAccessService.AUTHORIZATION_HEADER, required = false) String authorization,
      @RequestHeader(value = AgentInternalAccessService.RUN_ID_HEADER, required = false) String runId,
      @RequestHeader(value = AgentInternalAccessService.STEP_ID_HEADER, required = false) String stepId
  ) {
    verifyCall(serviceToken, authorization, runId, stepId);
    return Result.success(toolService.listResumes());
  }

  @GetMapping("/resumes/{resumeId}")
  public Result<AgentResumeDetail> getResumeDetail(
      @PathVariable Long resumeId,
      @RequestHeader(value = AgentInternalAccessService.TOKEN_HEADER, required = false) String serviceToken,
      @RequestHeader(value = AgentInternalAccessService.AUTHORIZATION_HEADER, required = false) String authorization,
      @RequestHeader(value = AgentInternalAccessService.RUN_ID_HEADER, required = false) String runId,
      @RequestHeader(value = AgentInternalAccessService.STEP_ID_HEADER, required = false) String stepId
  ) {
    verifyCall(serviceToken, authorization, runId, stepId);
    return Result.success(toolService.getResumeDetail(requireId(resumeId, "resumeId")));
  }

  @GetMapping("/jobs/{jobId}")
  public Result<AgentJobSnapshot> getJob(
      @PathVariable Long jobId,
      @RequestHeader(value = AgentInternalAccessService.TOKEN_HEADER, required = false) String serviceToken,
      @RequestHeader(value = AgentInternalAccessService.AUTHORIZATION_HEADER, required = false) String authorization,
      @RequestHeader(value = AgentInternalAccessService.RUN_ID_HEADER, required = false) String runId,
      @RequestHeader(value = AgentInternalAccessService.STEP_ID_HEADER, required = false) String stepId
  ) {
    verifyCall(serviceToken, authorization, runId, stepId);
    return Result.success(toolService.getJob(requireId(jobId, "jobId")));
  }

  @PostMapping("/job-match-tasks")
  public Result<AgentJobMatchTask> startJobMatch(
      @RequestBody AgentJobMatchCommand command,
      @RequestHeader(value = AgentInternalAccessService.TOKEN_HEADER, required = false) String serviceToken,
      @RequestHeader(value = AgentInternalAccessService.AUTHORIZATION_HEADER, required = false) String authorization,
      @RequestHeader(value = AgentInternalAccessService.RUN_ID_HEADER, required = false) String runId,
      @RequestHeader(value = AgentInternalAccessService.STEP_ID_HEADER, required = false) String stepId,
      @RequestHeader(value = AgentInternalAccessService.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey
  ) {
    verifyCall(serviceToken, authorization, runId, stepId);
    requireJobMatchCommand(command);
    return Result.success(toolService.startJobMatch(
        command,
        internalAccessService.requireIdempotencyKey(idempotencyKey)
    ));
  }

  @GetMapping("/job-match-tasks/{taskId}")
  public Result<AgentJobMatchTask> getJobMatchTask(
      @PathVariable Long taskId,
      @RequestHeader(value = AgentInternalAccessService.TOKEN_HEADER, required = false) String serviceToken,
      @RequestHeader(value = AgentInternalAccessService.AUTHORIZATION_HEADER, required = false) String authorization,
      @RequestHeader(value = AgentInternalAccessService.RUN_ID_HEADER, required = false) String runId,
      @RequestHeader(value = AgentInternalAccessService.STEP_ID_HEADER, required = false) String stepId
  ) {
    verifyCall(serviceToken, authorization, runId, stepId);
    return Result.success(toolService.getJobMatchTask(requireId(taskId, "taskId")));
  }

  @GetMapping("/job-match-reports/{reportId}")
  public Result<AgentJobMatchReport> getMatchReport(
      @PathVariable Long reportId,
      @RequestHeader(value = AgentInternalAccessService.TOKEN_HEADER, required = false) String serviceToken,
      @RequestHeader(value = AgentInternalAccessService.AUTHORIZATION_HEADER, required = false) String authorization,
      @RequestHeader(value = AgentInternalAccessService.RUN_ID_HEADER, required = false) String runId,
      @RequestHeader(value = AgentInternalAccessService.STEP_ID_HEADER, required = false) String stepId
  ) {
    verifyCall(serviceToken, authorization, runId, stepId);
    return Result.success(toolService.getMatchReport(requireId(reportId, "reportId")));
  }

  @PostMapping("/resume-improvement-plans")
  public Result<AgentResumeImprovementPlan> createImprovementPlan(
      @RequestBody AgentResumeImprovementPlanCommand command,
      @RequestHeader(value = AgentInternalAccessService.TOKEN_HEADER, required = false) String serviceToken,
      @RequestHeader(value = AgentInternalAccessService.AUTHORIZATION_HEADER, required = false) String authorization,
      @RequestHeader(value = AgentInternalAccessService.RUN_ID_HEADER, required = false) String runId,
      @RequestHeader(value = AgentInternalAccessService.STEP_ID_HEADER, required = false) String stepId,
      @RequestHeader(value = AgentInternalAccessService.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey
  ) {
    verifyCall(serviceToken, authorization, runId, stepId);
    if (command == null || command.matchReportId() == null || command.matchReportId() <= 0) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "matchReportId 必须为正整数");
    }
    if (command.strategy() == null || command.strategy().isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "strategy 不能为空");
    }
    if (command.rationale() == null || command.rationale().isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "rationale 不能为空");
    }
    if (command.prioritizedGaps() == null || command.prioritizedGaps().isEmpty()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "prioritizedGaps 不能为空");
    }
    return Result.success(toolService.createImprovementPlan(
        command,
        internalAccessService.requireIdempotencyKey(idempotencyKey)
    ));
  }

  @GetMapping("/resume-improvement-plans/{planId}")
  public Result<AgentResumeImprovementPlan> getImprovementPlan(
      @PathVariable Long planId,
      @RequestHeader(value = AgentInternalAccessService.TOKEN_HEADER, required = false) String serviceToken,
      @RequestHeader(value = AgentInternalAccessService.AUTHORIZATION_HEADER, required = false) String authorization,
      @RequestHeader(value = AgentInternalAccessService.RUN_ID_HEADER, required = false) String runId,
      @RequestHeader(value = AgentInternalAccessService.STEP_ID_HEADER, required = false) String stepId
  ) {
    verifyCall(serviceToken, authorization, runId, stepId);
    return Result.success(toolService.getImprovementPlan(requireId(planId, "planId")));
  }

  @GetMapping("/interview-sessions/{sessionId}/turn-context")
  public Result<AgentInterviewTurnContext> getInterviewTurnContext(
      @PathVariable String sessionId,
      @RequestHeader(value = AgentInternalAccessService.TOKEN_HEADER, required = false) String serviceToken,
      @RequestHeader(value = AgentInternalAccessService.AUTHORIZATION_HEADER, required = false) String authorization,
      @RequestHeader(value = AgentInternalAccessService.RUN_ID_HEADER, required = false) String runId,
      @RequestHeader(value = AgentInternalAccessService.STEP_ID_HEADER, required = false) String stepId
  ) {
    verifyCall(serviceToken, authorization, runId, stepId);
    return Result.success(toolService.getInterviewTurnContext(requireSessionId(sessionId)));
  }

  @GetMapping("/interview-planning-context")
  public Result<AgentInterviewPlanningContext> getInterviewPlanningContext(
      @RequestHeader(value = AgentInternalAccessService.TOKEN_HEADER, required = false) String serviceToken,
      @RequestHeader(value = AgentInternalAccessService.AUTHORIZATION_HEADER, required = false) String authorization,
      @RequestHeader(value = AgentInternalAccessService.RUN_ID_HEADER, required = false) String runId,
      @RequestHeader(value = AgentInternalAccessService.STEP_ID_HEADER, required = false) String stepId
  ) {
    verifyCall(serviceToken, authorization, runId, stepId);
    return Result.success(toolService.getInterviewPlanningContext());
  }

  @PostMapping("/interview-sessions")
  public Result<AgentInterviewSession> createInterviewSession(
      @RequestBody AgentCreateInterviewSessionCommand command,
      @RequestHeader(value = AgentInternalAccessService.TOKEN_HEADER, required = false) String serviceToken,
      @RequestHeader(value = AgentInternalAccessService.AUTHORIZATION_HEADER, required = false) String authorization,
      @RequestHeader(value = AgentInternalAccessService.RUN_ID_HEADER, required = false) String runId,
      @RequestHeader(value = AgentInternalAccessService.STEP_ID_HEADER, required = false) String stepId,
      @RequestHeader(value = AgentInternalAccessService.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey
  ) {
    verifyCall(serviceToken, authorization, runId, stepId);
    requireInterviewCommand(command);
    return Result.success(toolService.createInterviewSession(
        command,
        internalAccessService.requireIdempotencyKey(idempotencyKey)
    ));
  }

  @PostMapping("/interview-sessions/{sessionId}/turns")
  public Result<AgentInterviewTurnResult> applyInterviewTurn(
      @PathVariable String sessionId,
      @RequestBody AgentInterviewTurnCommand command,
      @RequestHeader(value = AgentInternalAccessService.TOKEN_HEADER, required = false) String serviceToken,
      @RequestHeader(value = AgentInternalAccessService.AUTHORIZATION_HEADER, required = false) String authorization,
      @RequestHeader(value = AgentInternalAccessService.RUN_ID_HEADER, required = false) String runId,
      @RequestHeader(value = AgentInternalAccessService.STEP_ID_HEADER, required = false) String stepId,
      @RequestHeader(value = AgentInternalAccessService.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey
  ) {
    verifyCall(serviceToken, authorization, runId, stepId);
    internalAccessService.requireIdempotencyKey(idempotencyKey);
    return Result.success(toolService.applyInterviewTurn(requireSessionId(sessionId), command));
  }

  private void verifyCall(String serviceToken, String authorization, String runId, String stepId) {
    internalAccessService.verifyToolCall(serviceToken, authorization, runId, stepId);
  }

  private void requireJobMatchCommand(AgentJobMatchCommand command) {
    if (command == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "岗位匹配参数不能为空");
    }
    requireId(command.resumeId(), "resumeId");
    requireId(command.jobId(), "jobId");
  }

  private Long requireId(Long id, String fieldName) {
    if (id == null || id <= 0) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 必须为正整数");
    }
    return id;
  }

  private void requireInterviewCommand(AgentCreateInterviewSessionCommand command) {
    if (command == null || command.blueprint() == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent 面试参数和蓝图不能为空");
    }
    if (command.questionCount() < 3 || command.questionCount() > 20) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "questionCount 必须在 3 到 20 之间");
    }
    if (command.skillId() == null || command.skillId().isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "skillId 不能为空");
    }
  }

  private String requireSessionId(String sessionId) {
    if (sessionId == null || sessionId.isBlank() || sessionId.length() > 36) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "sessionId 无效");
    }
    return sessionId.trim();
  }
}
