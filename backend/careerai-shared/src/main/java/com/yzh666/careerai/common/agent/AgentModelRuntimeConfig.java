package com.yzh666.careerai.common.agent;

/**
 * Internal-only runtime snapshot used by the Python Agent model factory.
 * The API key must never be returned by a public controller or written to logs.
 */
public record AgentModelRuntimeConfig(
    String providerId,
    String baseUrl,
    String apiKey,
    String model,
    Double temperature,
    String configVersion
) {
}
