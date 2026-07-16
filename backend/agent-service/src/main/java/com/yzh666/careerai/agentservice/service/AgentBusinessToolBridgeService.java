package com.yzh666.careerai.agentservice.service;

import com.yzh666.careerai.agentservice.client.AgentToolResponse;
import com.yzh666.careerai.agentservice.client.CareerAiBusinessToolClient;
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
import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentBusinessToolBridgeService {

  private final CareerAiBusinessToolClient client;
  private final AgentInternalAccessService internalAccessService;

  public List<AgentResumeSummary> listResumes(AgentToolCallContext context) {
    return unwrap(client.listResumes(token(), auth(context), run(context), step(context)));
  }

  public AgentResumeDetail getResumeDetail(Long resumeId, AgentToolCallContext context) {
    return unwrap(client.getResumeDetail(resumeId, token(), auth(context), run(context), step(context)));
  }

  public AgentJobSnapshot getJob(Long jobId, AgentToolCallContext context) {
    return unwrap(client.getJob(jobId, token(), auth(context), run(context), step(context)));
  }

  public AgentJobMatchTask startJobMatch(
      AgentJobMatchCommand command,
      AgentToolCallContext context,
      String idempotencyKey
  ) {
    return unwrap(client.startJobMatch(
        command,
        token(),
        auth(context),
        run(context),
        step(context),
        idempotencyKey
    ));
  }

  public AgentJobMatchTask getJobMatchTask(Long taskId, AgentToolCallContext context) {
    return unwrap(client.getJobMatchTask(taskId, token(), auth(context), run(context), step(context)));
  }

  public AgentJobMatchReport getMatchReport(Long reportId, AgentToolCallContext context) {
    return unwrap(client.getMatchReport(reportId, token(), auth(context), run(context), step(context)));
  }

  public AgentResumeImprovementPlan createImprovementPlan(
      AgentResumeImprovementPlanCommand command,
      AgentToolCallContext context,
      String idempotencyKey
  ) {
    return unwrap(client.createImprovementPlan(
        command,
        token(),
        auth(context),
        run(context),
        step(context),
        idempotencyKey
    ));
  }

  public AgentResumeImprovementPlan getImprovementPlan(Long planId, AgentToolCallContext context) {
    return unwrap(client.getImprovementPlan(planId, token(), auth(context), run(context), step(context)));
  }

  public AgentInterviewTurnContext getInterviewTurnContext(
      String sessionId,
      AgentToolCallContext context
  ) {
    return unwrap(client.getInterviewTurnContext(
        sessionId, token(), auth(context), run(context), step(context)));
  }

  public AgentInterviewPlanningContext getInterviewPlanningContext(
      AgentToolCallContext context
  ) {
    return unwrap(client.getInterviewPlanningContext(
        token(), auth(context), run(context), step(context)));
  }

  public AgentInterviewSession createInterviewSession(
      AgentCreateInterviewSessionCommand command,
      AgentToolCallContext context,
      String idempotencyKey
  ) {
    return unwrap(client.createInterviewSession(
        command,
        token(),
        auth(context),
        run(context),
        step(context),
        idempotencyKey
    ));
  }

  public AgentInterviewTurnResult applyInterviewTurn(
      String sessionId,
      AgentInterviewTurnCommand command,
      AgentToolCallContext context,
      String idempotencyKey
  ) {
    return unwrap(client.applyInterviewTurn(
        sessionId,
        command,
        token(),
        auth(context),
        run(context),
        step(context),
        idempotencyKey
    ));
  }

  private <T> T unwrap(AgentToolResponse<T> response) {
    if (response == null || !response.isSuccess() || response.data() == null) {
      String message = response == null ? "核心应用未返回业务数据" : response.message();
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Agent Tool 调用失败: " + message);
    }
    return response.data();
  }

  private String token() {
    return internalAccessService.requireServiceToken();
  }

  private String auth(AgentToolCallContext context) {
    return context.authorization();
  }

  private String run(AgentToolCallContext context) {
    return context.runId();
  }

  private String step(AgentToolCallContext context) {
    return context.stepId();
  }
}
