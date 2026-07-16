package com.yzh666.careerai.agentservice.service;

import com.yzh666.careerai.agentservice.client.CareerAiAgentConfigClient;
import com.yzh666.careerai.agentservice.client.AgentModelConfigResponse;
import com.yzh666.careerai.common.agent.AgentInternalAccessService;
import com.yzh666.careerai.common.agent.AgentModelRuntimeConfig;
import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentModelConfigBridgeService {

  private final CareerAiAgentConfigClient client;
  private final AgentInternalAccessService internalAccessService;

  public AgentModelRuntimeConfig getModelConfig() {
    AgentModelConfigResponse result = client.getModelConfig(
        internalAccessService.requireServiceToken()
    );
    if (result == null || !result.isSuccess() || result.data() == null) {
      String message = result == null ? "核心应用未返回配置" : result.message();
      throw new BusinessException(
          ErrorCode.PROVIDER_CONFIG_READ_FAILED,
          "读取 Agent 模型配置失败: " + message
      );
    }
    return result.data();
  }
}
