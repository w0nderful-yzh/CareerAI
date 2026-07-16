package com.yzh666.careerai.common.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yzh666.careerai.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class AgentInternalAccessServiceTest {

  private final AgentInternalAccessService service = new AgentInternalAccessService("internal-token");

  @Test
  void acceptsCompleteToolCallContext() {
    service.verifyToolCall("internal-token", "Bearer user-jwt", "run-1", "step-1");
    assertEquals("run-1:write", service.requireIdempotencyKey(" run-1:write "));
  }

  @Test
  void rejectsToolCallWithoutUserAuthorization() {
    assertThrows(
        BusinessException.class,
        () -> service.verifyToolCall("internal-token", null, "run-1", "step-1")
    );
  }

  @Test
  void rejectsWriteWithoutIdempotencyKey() {
    assertThrows(BusinessException.class, () -> service.requireIdempotencyKey(" "));
  }
}
