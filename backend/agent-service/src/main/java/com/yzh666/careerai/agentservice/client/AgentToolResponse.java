package com.yzh666.careerai.agentservice.client;

/** Feign 侧的统一响应壳；对应核心应用的 Result JSON。 */
public record AgentToolResponse<T>(
    Integer code,
    String message,
    T data
) {

  public boolean isSuccess() {
    return Integer.valueOf(200).equals(code);
  }
}
