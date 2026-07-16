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
import com.yzh666.careerai.common.exception.BusinessException;
import java.time.LocalDateTime;
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
}
