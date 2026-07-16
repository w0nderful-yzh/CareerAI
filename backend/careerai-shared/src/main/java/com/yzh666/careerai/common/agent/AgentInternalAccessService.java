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
}
