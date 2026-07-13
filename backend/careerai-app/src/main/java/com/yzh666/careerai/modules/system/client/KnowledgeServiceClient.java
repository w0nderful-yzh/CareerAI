package com.yzh666.careerai.modules.system.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@FeignClient(name = "knowledge-service")
public interface KnowledgeServiceClient {

  @GetMapping("/actuator/health")
  Map<String, Object> health();
}
