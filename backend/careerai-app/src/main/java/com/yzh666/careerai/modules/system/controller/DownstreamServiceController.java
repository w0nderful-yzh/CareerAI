package com.yzh666.careerai.modules.system.controller;

import com.yzh666.careerai.common.result.Result;
import com.yzh666.careerai.modules.system.dto.DownstreamServiceStatusDTO;
import com.yzh666.careerai.modules.system.service.DownstreamServiceHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DownstreamServiceController {

  private final DownstreamServiceHealthService healthService;

  @GetMapping("/api/system/downstreams/knowledge-service/health")
  public Result<DownstreamServiceStatusDTO> checkKnowledgeService() {
    return Result.success(healthService.checkKnowledgeService());
  }
}
