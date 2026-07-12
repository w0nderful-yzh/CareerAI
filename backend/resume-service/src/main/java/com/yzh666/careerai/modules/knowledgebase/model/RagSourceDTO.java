package com.yzh666.careerai.modules.knowledgebase.model;

/**
 * RAG 回答引用来源。
 */
public record RagSourceDTO(
    Long knowledgeBaseId,
    String knowledgeBaseName,
    String category,
    String originalFilename,
    Integer chunkIndex,
    String snippet,
    Double score
) {
}
