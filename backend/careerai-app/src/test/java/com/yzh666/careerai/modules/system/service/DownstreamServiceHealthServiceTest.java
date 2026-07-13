package com.yzh666.careerai.modules.system.service;

import com.yzh666.careerai.modules.system.dto.DownstreamServiceStatusDTO;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownstreamServiceHealthServiceTest {

  @Test
  void shouldReportKnowledgeServiceUp() {
    DownstreamServiceHealthService service = new DownstreamServiceHealthService(
        () -> Map.of("status", "UP")
    );

    DownstreamServiceStatusDTO status = service.checkKnowledgeService();

    assertEquals("knowledge-service", status.serviceName());
    assertEquals("UP", status.status());
    assertTrue(status.reachable());
  }

  @Test
  void shouldReportKnowledgeServiceDownWhenFeignFails() {
    DownstreamServiceHealthService service = new DownstreamServiceHealthService(() -> {
      throw new IllegalStateException("service unavailable");
    });

    DownstreamServiceStatusDTO status = service.checkKnowledgeService();

    assertEquals("knowledge-service", status.serviceName());
    assertEquals("DOWN", status.status());
    assertFalse(status.reachable());
    assertTrue(status.detail().contains("service unavailable"));
  }
}
