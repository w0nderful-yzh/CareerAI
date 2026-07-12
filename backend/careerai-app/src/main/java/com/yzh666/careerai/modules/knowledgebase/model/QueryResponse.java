package com.yzh666.careerai.modules.knowledgebase.model;

import java.util.List;

/**
 * 知识库查询响应
 */
public record QueryResponse(
    String answer,
    Long knowledgeBaseId,
    String knowledgeBaseName,
    List<RagSourceDTO> sources
) {
    public QueryResponse(String answer, Long knowledgeBaseId, String knowledgeBaseName) {
        this(answer, knowledgeBaseId, knowledgeBaseName, List.of());
    }
}
