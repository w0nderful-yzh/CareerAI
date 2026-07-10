package com.yzh666.careerai.modules.knowledgebase.model;

/**
 * 知识库查询响应
 */
public record QueryResponse(
    String answer,
    Long knowledgeBaseId,
    String knowledgeBaseName
) {}

