package com.yzh666.careerai.modules.knowledgebase.model;

import java.util.List;

/**
 * RAG 检索过滤条件。
 */
public record RagSearchFilter(
    Long userId,
    List<String> categories,
    String keyword
) {
    public RagSearchFilter {
        categories = categories == null
            ? List.of()
            : categories.stream()
                .filter(category -> category != null && !category.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    public static RagSearchFilter ofUser(Long userId) {
        return new RagSearchFilter(userId, List.of(), null);
    }
}
