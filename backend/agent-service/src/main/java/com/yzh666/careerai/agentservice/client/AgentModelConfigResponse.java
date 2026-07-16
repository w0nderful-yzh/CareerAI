package com.yzh666.careerai.agentservice.client;

import com.yzh666.careerai.common.agent.AgentModelRuntimeConfig;

public record AgentModelConfigResponse(
    Integer code,
    String message,
    AgentModelRuntimeConfig data
) {

  public boolean isSuccess() {
    return Integer.valueOf(200).equals(code);
  }
}
