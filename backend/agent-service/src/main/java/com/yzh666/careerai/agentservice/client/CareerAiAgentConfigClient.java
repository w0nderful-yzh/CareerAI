package com.yzh666.careerai.agentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
    name = "careerai-agent-config",
    url = "${app.agent.core-service-url:http://localhost:8080}"
)
public interface CareerAiAgentConfigClient {

  @GetMapping("/internal/agent/model-config")
  AgentModelConfigResponse getModelConfig(
      @RequestHeader("X-Agent-Service-Token") String serviceToken
  );
}
