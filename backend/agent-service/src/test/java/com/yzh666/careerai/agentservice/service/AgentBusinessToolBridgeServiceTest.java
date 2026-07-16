package com.yzh666.careerai.agentservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzh666.careerai.agentservice.client.AgentToolResponse;
import com.yzh666.careerai.agentservice.client.CareerAiBusinessToolClient;
import com.yzh666.careerai.common.agent.AgentInternalAccessService;
import com.yzh666.careerai.common.agent.tool.AgentJobMatchCommand;
import com.yzh666.careerai.common.agent.tool.AgentJobMatchTask;
import com.yzh666.careerai.common.agent.tool.AgentInterviewDecision;
import com.yzh666.careerai.common.agent.tool.AgentInterviewQuestion;
import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnCommand;
import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnEvaluation;
import com.yzh666.careerai.common.agent.tool.AgentNextQuestionIntent;
import com.yzh666.careerai.common.agent.tool.AgentInterviewTurnResult;
import com.yzh666.careerai.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentBusinessToolBridgeServiceTest {

  @Mock
  private CareerAiBusinessToolClient client;

  @Mock
  private AgentInternalAccessService internalAccessService;

  @InjectMocks
  private AgentBusinessToolBridgeService service;

  @Test
  void forwardsUserAndExecutionContextForWriteTool() {
    AgentToolCallContext context = new AgentToolCallContext("Bearer user-jwt", "run-1", "step-1");
    AgentJobMatchCommand command = new AgentJobMatchCommand(11L, 22L);
    AgentJobMatchTask task = new AgentJobMatchTask(
        91L,
        "PENDING",
        11L,
        22L,
        null,
        null,
        null,
        LocalDateTime.now(),
        LocalDateTime.now()
    );
    when(internalAccessService.requireServiceToken()).thenReturn("internal-token");
    when(client.startJobMatch(
        command,
        "internal-token",
        "Bearer user-jwt",
        "run-1",
        "step-1",
        "run-1:match"
    )).thenReturn(new AgentToolResponse<>(200, "success", task));

    AgentJobMatchTask result = service.startJobMatch(command, context, "run-1:match");

    assertEquals(91L, result.id());
    verify(client).startJobMatch(
        command,
        "internal-token",
        "Bearer user-jwt",
        "run-1",
        "step-1",
        "run-1:match"
    );
  }

  @Test
  void rejectsEmptyCoreResponse() {
    AgentToolCallContext context = new AgentToolCallContext("Bearer user-jwt", "run-1", "step-1");
    when(internalAccessService.requireServiceToken()).thenReturn("internal-token");
    when(client.getJob(22L, "internal-token", "Bearer user-jwt", "run-1", "step-1"))
        .thenReturn(new AgentToolResponse<>(9501, "岗位不存在", null));

    assertThrows(BusinessException.class, () -> service.getJob(22L, context));
  }

  @Test
  void forwardsAdaptiveInterviewDecisionAsBoundedWrite() {
    AgentToolCallContext context = new AgentToolCallContext("Bearer user-jwt", "run-2", "turn-1");
    AgentInterviewTurnCommand command = new AgentInterviewTurnCommand(
        0,
        "通过缓存空值处理。",
        "FOLLOW_UP",
        "需要继续验证缓存一致性。",
        72,
        "基础方案正确，但缺少一致性说明。",
        "KEEP",
        new AgentNextQuestionIntent(
            "PROJECT_EVIDENCE", "Redis 一致性", "REQ-1", "mid", true, 0,
            "验证缓存更新和数据库写入的一致性处理能力"),
        new AgentInterviewTurnEvaluation(
            true, 75, 65, 70, 60, null, 62, 72, 78, 70, 74,
            List.of("缺少一致性说明"), List.of(), List.of("缓存空值"), 82),
        null,
        "ANSWER"
    );
    AgentInterviewTurnResult result = new AgentInterviewTurnResult(
        "session-1",
        false,
        new AgentInterviewQuestion(1, "如何保证缓存一致性？", "REDIS", "Redis",
            null, null, null, null, true, 0, "REQ-1"),
        new AgentInterviewDecision(0, "FOLLOW_UP", "需要继续验证缓存一致性。", 72,
            "基础方案正确，但缺少一致性说明。", "KEEP", 1, "REQ-1", LocalDateTime.now()),
        1,
        4
    );
    when(internalAccessService.requireServiceToken()).thenReturn("internal-token");
    when(client.applyInterviewTurn(
        "session-1", command, "internal-token", "Bearer user-jwt", "run-2", "turn-1", "turn-key"
    )).thenReturn(new AgentToolResponse<>(200, "success", result));

    AgentInterviewTurnResult actual = service.applyInterviewTurn(
        "session-1", command, context, "turn-key");

    assertEquals("FOLLOW_UP", actual.decision().action());
    verify(client).applyInterviewTurn(
        "session-1", command, "internal-token", "Bearer user-jwt", "run-2", "turn-1", "turn-key"
    );
  }
}
