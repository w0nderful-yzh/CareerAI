package com.yzh666.careerai.agentservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.yzh666.careerai.agentservice.client.CareerAiAgentConfigClient;
import com.yzh666.careerai.agentservice.client.AgentModelConfigResponse;
import com.yzh666.careerai.common.agent.AgentInternalAccessService;
import com.yzh666.careerai.common.agent.AgentModelRuntimeConfig;
import com.yzh666.careerai.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentModelConfigBridgeServiceTest {

  @Mock
  private CareerAiAgentConfigClient client;

  @Mock
  private AgentInternalAccessService internalAccessService;

  @InjectMocks
  private AgentModelConfigBridgeService service;

  @Test
  void returnsCoreRuntimeConfig() {
    AgentModelRuntimeConfig config = new AgentModelRuntimeConfig(
        "dashscope",
        "https://example.test/v1",
        "secret",
        "qwen-plus",
        0.2,
        "v1"
    );
    when(internalAccessService.requireServiceToken()).thenReturn("internal-token");
    when(client.getModelConfig("internal-token"))
        .thenReturn(new AgentModelConfigResponse(200, "success", config));

    AgentModelRuntimeConfig result = service.getModelConfig();

    assertEquals("dashscope", result.providerId());
    assertEquals("v1", result.configVersion());
  }

  @Test
  void rejectsInvalidCoreResponse() {
    when(internalAccessService.requireServiceToken()).thenReturn("internal-token");
    when(client.getModelConfig("internal-token"))
        .thenReturn(new AgentModelConfigResponse(11004, "provider unavailable", null));

    assertThrows(BusinessException.class, service::getModelConfig);
  }
}
