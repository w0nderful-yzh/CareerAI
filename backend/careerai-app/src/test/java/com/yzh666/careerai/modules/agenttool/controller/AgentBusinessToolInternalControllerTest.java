package com.yzh666.careerai.modules.agenttool.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzh666.careerai.common.agent.AgentInternalAccessService;
import com.yzh666.careerai.common.agent.tool.AgentJobMatchCommand;
import com.yzh666.careerai.common.agent.tool.AgentJobMatchTask;
import com.yzh666.careerai.modules.agenttool.service.AgentBusinessToolService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentBusinessToolInternalControllerTest {

  @Mock
  private AgentInternalAccessService internalAccessService;

  @Mock
  private AgentBusinessToolService toolService;

  @InjectMocks
  private AgentBusinessToolInternalController controller;

  @Test
  void verifiesIdentityAndIdempotencyBeforeWrite() {
    AgentJobMatchCommand command = new AgentJobMatchCommand(11L, 22L);
    AgentJobMatchTask task = new AgentJobMatchTask(
        91L,
        "PENDING",
        11L,
        22L,
        null,
        null,
        null,
        null,
        null
    );
    when(internalAccessService.requireIdempotencyKey("run-1:match"))
        .thenReturn("run-1:match");
    when(toolService.startJobMatch(command, "run-1:match")).thenReturn(task);

    var result = controller.startJobMatch(
        command,
        "internal-token",
        "Bearer user-jwt",
        "run-1",
        "step-1",
        "run-1:match"
    );

    assertEquals(91L, result.getData().id());
    verify(internalAccessService).verifyToolCall(
        "internal-token",
        "Bearer user-jwt",
        "run-1",
        "step-1"
    );
    verify(toolService).startJobMatch(command, "run-1:match");
  }
}
