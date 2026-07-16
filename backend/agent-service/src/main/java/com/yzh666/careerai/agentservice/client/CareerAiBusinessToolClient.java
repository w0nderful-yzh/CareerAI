package com.yzh666.careerai.agentservice.client;

import com.yzh666.careerai.common.agent.AgentInternalAccessService;
import com.yzh666.careerai.common.agent.tool.AgentCreateInterviewSessionCommand;
import com.yzh666.careerai.common.agent.tool.AgentInterviewSession;
import com.yzh666.careerai.common.agent.tool.AgentJobMatchCommand;
import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnCommand;
import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnContext;
import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnResult;
import com.yzh666.careerai.common.agent.tool.AgentInterviewPlanningContext;
import com.yzh666.careerai.common.agent.tool.AgentJobMatchReport;
import com.yzh666.careerai.common.agent.tool.AgentJobMatchTask;
import com.yzh666.careerai.common.agent.tool.AgentJobSnapshot;
import com.yzh666.careerai.common.agent.tool.AgentResumeDetail;
import com.yzh666.careerai.common.agent.tool.AgentResumeImprovementPlan;
import com.yzh666.careerai.common.agent.tool.AgentResumeImprovementPlanCommand;
import com.yzh666.careerai.common.agent.tool.AgentResumeSummary;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
    name = "careerai-agent-business-tools",
    url = "${app.agent.core-service-url:http://localhost:8080}"
)
public interface CareerAiBusinessToolClient {

  @GetMapping("/internal/agent/tools/resumes")
  AgentToolResponse<List<AgentResumeSummary>> listResumes(
      @RequestHeader(AgentInternalAccessService.TOKEN_HEADER) String serviceToken,
      @RequestHeader(AgentInternalAccessService.AUTHORIZATION_HEADER) String authorization,
      @RequestHeader(AgentInternalAccessService.RUN_ID_HEADER) String runId,
      @RequestHeader(AgentInternalAccessService.STEP_ID_HEADER) String stepId
  );

  @GetMapping("/internal/agent/tools/resumes/{resumeId}")
  AgentToolResponse<AgentResumeDetail> getResumeDetail(
      @PathVariable Long resumeId,
      @RequestHeader(AgentInternalAccessService.TOKEN_HEADER) String serviceToken,
      @RequestHeader(AgentInternalAccessService.AUTHORIZATION_HEADER) String authorization,
      @RequestHeader(AgentInternalAccessService.RUN_ID_HEADER) String runId,
      @RequestHeader(AgentInternalAccessService.STEP_ID_HEADER) String stepId
  );

  @GetMapping("/internal/agent/tools/jobs/{jobId}")
  AgentToolResponse<AgentJobSnapshot> getJob(
      @PathVariable Long jobId,
      @RequestHeader(AgentInternalAccessService.TOKEN_HEADER) String serviceToken,
      @RequestHeader(AgentInternalAccessService.AUTHORIZATION_HEADER) String authorization,
      @RequestHeader(AgentInternalAccessService.RUN_ID_HEADER) String runId,
      @RequestHeader(AgentInternalAccessService.STEP_ID_HEADER) String stepId
  );

  @PostMapping("/internal/agent/tools/job-match-tasks")
  AgentToolResponse<AgentJobMatchTask> startJobMatch(
      @RequestBody AgentJobMatchCommand command,
      @RequestHeader(AgentInternalAccessService.TOKEN_HEADER) String serviceToken,
      @RequestHeader(AgentInternalAccessService.AUTHORIZATION_HEADER) String authorization,
      @RequestHeader(AgentInternalAccessService.RUN_ID_HEADER) String runId,
      @RequestHeader(AgentInternalAccessService.STEP_ID_HEADER) String stepId,
      @RequestHeader(AgentInternalAccessService.IDEMPOTENCY_KEY_HEADER) String idempotencyKey
  );

  @GetMapping("/internal/agent/tools/job-match-tasks/{taskId}")
  AgentToolResponse<AgentJobMatchTask> getJobMatchTask(
      @PathVariable Long taskId,
      @RequestHeader(AgentInternalAccessService.TOKEN_HEADER) String serviceToken,
      @RequestHeader(AgentInternalAccessService.AUTHORIZATION_HEADER) String authorization,
      @RequestHeader(AgentInternalAccessService.RUN_ID_HEADER) String runId,
      @RequestHeader(AgentInternalAccessService.STEP_ID_HEADER) String stepId
  );

  @GetMapping("/internal/agent/tools/job-match-reports/{reportId}")
  AgentToolResponse<AgentJobMatchReport> getMatchReport(
      @PathVariable Long reportId,
      @RequestHeader(AgentInternalAccessService.TOKEN_HEADER) String serviceToken,
      @RequestHeader(AgentInternalAccessService.AUTHORIZATION_HEADER) String authorization,
      @RequestHeader(AgentInternalAccessService.RUN_ID_HEADER) String runId,
      @RequestHeader(AgentInternalAccessService.STEP_ID_HEADER) String stepId
  );

  @PostMapping("/internal/agent/tools/resume-improvement-plans")
  AgentToolResponse<AgentResumeImprovementPlan> createImprovementPlan(
      @RequestBody AgentResumeImprovementPlanCommand command,
      @RequestHeader(AgentInternalAccessService.TOKEN_HEADER) String serviceToken,
      @RequestHeader(AgentInternalAccessService.AUTHORIZATION_HEADER) String authorization,
      @RequestHeader(AgentInternalAccessService.RUN_ID_HEADER) String runId,
      @RequestHeader(AgentInternalAccessService.STEP_ID_HEADER) String stepId,
      @RequestHeader(AgentInternalAccessService.IDEMPOTENCY_KEY_HEADER) String idempotencyKey
  );

  @GetMapping("/internal/agent/tools/resume-improvement-plans/{planId}")
  AgentToolResponse<AgentResumeImprovementPlan> getImprovementPlan(
      @PathVariable Long planId,
      @RequestHeader(AgentInternalAccessService.TOKEN_HEADER) String serviceToken,
      @RequestHeader(AgentInternalAccessService.AUTHORIZATION_HEADER) String authorization,
      @RequestHeader(AgentInternalAccessService.RUN_ID_HEADER) String runId,
      @RequestHeader(AgentInternalAccessService.STEP_ID_HEADER) String stepId
  );

  @GetMapping("/internal/agent/tools/interview-sessions/{sessionId}/turn-context")
  AgentToolResponse<AgentInterviewTurnContext> getInterviewTurnContext(
      @PathVariable String sessionId,
      @RequestHeader(AgentInternalAccessService.TOKEN_HEADER) String serviceToken,
      @RequestHeader(AgentInternalAccessService.AUTHORIZATION_HEADER) String authorization,
      @RequestHeader(AgentInternalAccessService.RUN_ID_HEADER) String runId,
      @RequestHeader(AgentInternalAccessService.STEP_ID_HEADER) String stepId
  );

  @GetMapping("/internal/agent/tools/interview-planning-context")
  AgentToolResponse<AgentInterviewPlanningContext> getInterviewPlanningContext(
      @RequestHeader(AgentInternalAccessService.TOKEN_HEADER) String serviceToken,
      @RequestHeader(AgentInternalAccessService.AUTHORIZATION_HEADER) String authorization,
      @RequestHeader(AgentInternalAccessService.RUN_ID_HEADER) String runId,
      @RequestHeader(AgentInternalAccessService.STEP_ID_HEADER) String stepId
  );

  @PostMapping("/internal/agent/tools/interview-sessions")
  AgentToolResponse<AgentInterviewSession> createInterviewSession(
      @RequestBody AgentCreateInterviewSessionCommand command,
      @RequestHeader(AgentInternalAccessService.TOKEN_HEADER) String serviceToken,
      @RequestHeader(AgentInternalAccessService.AUTHORIZATION_HEADER) String authorization,
      @RequestHeader(AgentInternalAccessService.RUN_ID_HEADER) String runId,
      @RequestHeader(AgentInternalAccessService.STEP_ID_HEADER) String stepId,
      @RequestHeader(AgentInternalAccessService.IDEMPOTENCY_KEY_HEADER) String idempotencyKey
  );

  @PostMapping("/internal/agent/tools/interview-sessions/{sessionId}/turns")
  AgentToolResponse<AgentInterviewTurnResult> applyInterviewTurn(
      @PathVariable String sessionId,
      @RequestBody AgentInterviewTurnCommand command,
      @RequestHeader(AgentInternalAccessService.TOKEN_HEADER) String serviceToken,
      @RequestHeader(AgentInternalAccessService.AUTHORIZATION_HEADER) String authorization,
      @RequestHeader(AgentInternalAccessService.RUN_ID_HEADER) String runId,
      @RequestHeader(AgentInternalAccessService.STEP_ID_HEADER) String stepId,
      @RequestHeader(AgentInternalAccessService.IDEMPOTENCY_KEY_HEADER) String idempotencyKey
  );
}
