package com.yzh666.careerai.common.agent;

import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AgentInternalAccessService {

  public static final String TOKEN_HEADER = "X-Agent-Service-Token";
  public static final String AUTHORIZATION_HEADER = "Authorization";
  public static final String RUN_ID_HEADER = "X-Agent-Run-Id";
  public static final String STEP_ID_HEADER = "X-Agent-Step-Id";
  public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

  private final String serviceToken;

  public AgentInternalAccessService(
      @Value("${app.agent.internal.service-token:}") String serviceToken
  ) {
    this.serviceToken = serviceToken == null ? "" : serviceToken.trim();
  }

  public void verify(String suppliedToken) {
    if (serviceToken.isBlank()) {
      throw new BusinessException(
          ErrorCode.PROVIDER_CONFIG_READ_FAILED,
          "Agent 内部服务令牌未配置"
      );
    }
    byte[] expected = serviceToken.getBytes(StandardCharsets.UTF_8);
    byte[] actual = suppliedToken == null
        ? new byte[0]
        : suppliedToken.getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(expected, actual)) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Agent 内部服务令牌无效");
    }
  }

  public String requireServiceToken() {
    if (serviceToken.isBlank()) {
      throw new BusinessException(
          ErrorCode.PROVIDER_CONFIG_READ_FAILED,
          "Agent 内部服务令牌未配置"
      );
    }
    return serviceToken;
  }

  /**
   * 校验一次业务 Tool 调用的最小上下文。
   * 用户身份只认原始 JWT，runId/stepId 用于把一次 Agent 执行串起来。
   */
  public void verifyToolCall(
      String suppliedToken,
      String authorization,
      String runId,
      String stepId
  ) {
    verify(suppliedToken);
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Agent Tool 调用缺少用户 Authorization");
    }
    requireContextValue(runId, RUN_ID_HEADER);
    requireContextValue(stepId, STEP_ID_HEADER);
  }

  public String requireIdempotencyKey(String idempotencyKey) {
    String normalized = requireContextValue(idempotencyKey, IDEMPOTENCY_KEY_HEADER);
    if (normalized.length() > 120) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Idempotency-Key 长度不能超过 120");
    }
    return normalized;
  }

  private String requireContextValue(String value, String headerName) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent Tool 调用缺少 " + headerName);
    }
    return value.trim();
  }
}
