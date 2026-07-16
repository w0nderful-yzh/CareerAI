package com.yzh666.careerai.modules.llmprovider.dto;

public record DefaultProviderDTO(
    String defaultProvider,
    String defaultEmbeddingProvider,
    String defaultAgentProvider
) {
    public DefaultProviderDTO(String defaultProvider) {
        this(defaultProvider, null, null);
    }

    public DefaultProviderDTO(String defaultProvider, String defaultEmbeddingProvider) {
        this(defaultProvider, defaultEmbeddingProvider, null);
    }
}
