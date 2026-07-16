package com.yzh666.careerai.modules.llmprovider.controller;

import com.yzh666.careerai.common.agent.AgentInternalAccessService;
import com.yzh666.careerai.common.agent.AgentModelRuntimeConfig;
import com.yzh666.careerai.common.result.Result;
import com.yzh666.careerai.modules.llmprovider.service.LlmProviderConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/agent")
@RequiredArgsConstructor
public class AgentModelConfigInternalController {

  private final AgentInternalAccessService internalAccessService;
  private final LlmProviderConfigService configService;

  @GetMapping("/model-config")
  public Result<AgentModelRuntimeConfig> getModelConfig(
      @RequestHeader(value = AgentInternalAccessService.TOKEN_HEADER, required = false)
      String serviceToken
  ) {
    internalAccessService.verify(serviceToken);
    return Result.success(configService.getAgentModelRuntimeConfig());
  }
}
