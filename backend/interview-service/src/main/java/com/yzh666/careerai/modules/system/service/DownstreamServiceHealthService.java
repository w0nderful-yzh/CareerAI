package com.yzh666.careerai.modules.system.service;

import com.yzh666.careerai.modules.system.client.KnowledgeServiceClient;
import com.yzh666.careerai.modules.system.dto.DownstreamServiceStatusDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class DownstreamServiceHealthService {

  private static final String KNOWLEDGE_SERVICE = "knowledge-service";

  private final KnowledgeServiceClient knowledgeServiceClient;

  public DownstreamServiceStatusDTO checkKnowledgeService() {
    try {
      Map<String, Object> health = knowledgeServiceClient.health();
      String status = String.valueOf(health.getOrDefault("status", "UNKNOWN"));
      boolean reachable = "UP".equalsIgnoreCase(status);
      return new DownstreamServiceStatusDTO(
          KNOWLEDGE_SERVICE,
          status,
          reachable,
          "OpenFeign -> " + KNOWLEDGE_SERVICE + " /actuator/health"
      );
    } catch (Exception e) {
      return new DownstreamServiceStatusDTO(
          KNOWLEDGE_SERVICE,
          "DOWN",
          false,
          e.getClass().getSimpleName() + ": " + e.getMessage()
      );
    }
  }
}
