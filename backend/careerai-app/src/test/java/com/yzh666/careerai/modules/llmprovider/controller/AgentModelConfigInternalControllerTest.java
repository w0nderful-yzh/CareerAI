package com.yzh666.careerai.modules.llmprovider.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yzh666.careerai.common.agent.AgentInternalAccessService;
import com.yzh666.careerai.common.agent.AgentModelRuntimeConfig;
import com.yzh666.careerai.common.result.Result;
import com.yzh666.careerai.modules.llmprovider.service.LlmProviderConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentModelConfigInternalControllerTest {

  @Mock
  private AgentInternalAccessService internalAccessService;

  @Mock
  private LlmProviderConfigService configService;

  @InjectMocks
  private AgentModelConfigInternalController controller;

  @Test
  void verifiesInternalTokenBeforeReturningRuntimeConfig() {
    AgentModelRuntimeConfig config = new AgentModelRuntimeConfig(
        "dashscope", "https://example.test/v1", "secret", "qwen", 0.2, "v1");
    when(configService.getAgentModelRuntimeConfig()).thenReturn(config);

    Result<AgentModelRuntimeConfig> result = controller.getModelConfig("internal-token");

    verify(internalAccessService).verify("internal-token");
    assertEquals("dashscope", result.getData().providerId());
  }
}
