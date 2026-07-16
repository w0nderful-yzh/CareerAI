package com.yzh666.careerai.agentservice.controller;

import com.yzh666.careerai.agentservice.service.AgentModelConfigBridgeService;
import com.yzh666.careerai.common.agent.AgentInternalAccessService;
import com.yzh666.careerai.common.agent.AgentModelRuntimeConfig;
import com.yzh666.careerai.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/agent")
@RequiredArgsConstructor
public class AgentModelConfigController {

  private final AgentInternalAccessService internalAccessService;
  private final AgentModelConfigBridgeService bridgeService;

  @GetMapping("/model-config")
  public Result<AgentModelRuntimeConfig> getModelConfig(
      @RequestHeader(value = AgentInternalAccessService.TOKEN_HEADER, required = false)
      String serviceToken
  ) {
    internalAccessService.verify(serviceToken);
    return Result.success(bridgeService.getModelConfig());
  }
}
