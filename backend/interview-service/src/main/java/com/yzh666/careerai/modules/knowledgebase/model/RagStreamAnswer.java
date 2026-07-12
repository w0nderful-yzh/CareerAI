package com.yzh666.careerai.modules.knowledgebase.model;

import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 流式 RAG 回答和本轮检索来源。
 */
public record RagStreamAnswer(
    Flux<String> content,
    List<RagSourceDTO> sources
) {
}
