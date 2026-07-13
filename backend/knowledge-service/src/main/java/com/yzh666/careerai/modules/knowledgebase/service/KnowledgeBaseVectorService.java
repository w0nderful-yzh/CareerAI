package com.yzh666.careerai.modules.knowledgebase.service;

import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.common.transaction.TransactionalExecutor;
import com.yzh666.careerai.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.yzh666.careerai.modules.knowledgebase.model.RagSearchFilter;
import com.yzh666.careerai.modules.knowledgebase.repository.KnowledgeBaseRepository;
import com.yzh666.careerai.modules.knowledgebase.repository.VectorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库向量存储服务
 * 负责文档分块、向量化和检索
 */
@Slf4j
@Service
public class KnowledgeBaseVectorService {
    
    /**
     * 阿里云 DashScope Embedding API 批量大小限制
     */
    private static final int MAX_BATCH_SIZE = 10;
    private static final String TEMP_KB_ID_PREFIX = "pending:";
    private static final String METADATA_KB_ID = "kb_id";
    private static final String METADATA_TARGET_KB_ID = "kb_target_id";
    private static final String METADATA_VECTOR_JOB_ID = "kb_vector_job_id";
    private static final String METADATA_USER_ID = "user_id";
    private static final String METADATA_KB_NAME = "kb_name";
    private static final String METADATA_KB_CATEGORY = "kb_category";
    private static final String METADATA_ORIGINAL_FILENAME = "kb_original_filename";
    private static final String METADATA_CHUNK_INDEX = "chunk_index";
    private final VectorStore vectorStore;
    private final TextSplitter textSplitter;
    private final VectorRepository vectorRepository;
    private final TransactionalExecutor transactionalExecutor;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    public KnowledgeBaseVectorService(
        VectorStore vectorStore,
        VectorRepository vectorRepository,
        TransactionalExecutor transactionalExecutor,
        KnowledgeBaseRepository knowledgeBaseRepository
    ) {
        this.vectorStore = vectorStore;
        this.vectorRepository = vectorRepository;
        this.transactionalExecutor = transactionalExecutor;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        // 使用 TokenTextSplitter 默认配置，每个 chunk 约 800 tokens，基于标点边界切分（无重叠）
        this.textSplitter = TokenTextSplitter.builder().build();
    }

    KnowledgeBaseVectorService(VectorStore vectorStore, VectorRepository vectorRepository) {
        this(vectorStore, vectorRepository, null, null);
    }

    /**
     * 将知识库内容向量化并存储
     * @param knowledgeBaseId 知识库ID
     * @param content 知识库文本内容
     */
    public void vectorizeAndStore(Long knowledgeBaseId, String content) {
        String jobId = null;
        try {
            if (knowledgeBaseId == null) {
                throw new IllegalArgumentException("knowledgeBaseId不能为空");
            }
            jobId = UUID.randomUUID().toString();
            log.info("开始向量化知识库: kbId={}, jobId={}, contentLength={}",
                knowledgeBaseId, jobId, content.length());
            KnowledgeBaseEntity knowledgeBase = loadKnowledgeBase(knowledgeBaseId);

            // 1. 将文本分块
            List<Document> chunks = textSplitter.apply(
                List.of(new Document(content))
            );
            
            log.info("文本分块完成: {} 个chunks", chunks.size());
            
            // 2. 为每个 chunk 添加临时 metadata，成功后再提升为正式 kb_id。
            applyPendingMetadata(chunks, knowledgeBase, jobId);

            // 3. 分批向量化并存储（阿里云 DashScope API 限制 batch size <= 10）
            int totalChunks = chunks.size();
            int batchCount = (totalChunks + MAX_BATCH_SIZE - 1) / MAX_BATCH_SIZE; // 向上取整
            log.info("开始分批向量化: 总共 {} 个chunks，分 {} 批处理，每批最多 {} 个",
                    totalChunks, batchCount, MAX_BATCH_SIZE);
            for (int i = 0; i < batchCount; i++) {
                int start = i * MAX_BATCH_SIZE;
                int end = Math.min(start + MAX_BATCH_SIZE, totalChunks);
                List<Document> batch = chunks.subList(start, end);
                log.debug("处理第 {}/{} 批: chunks {}-{}", i + 1, batchCount, start + 1, end);
                vectorStore.add(batch);
            }
            activateVectorJob(knowledgeBaseId, jobId);
            log.info("知识库向量化完成: kbId={}, jobId={}, chunks={}, batches={}",
                    knowledgeBaseId, jobId, totalChunks, batchCount);
        } catch (Exception e) {
            cleanupPendingVectorJob(knowledgeBaseId, jobId);
            log.error("向量化知识库失败: kbId={}, jobId={}, error={}",
                knowledgeBaseId, jobId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_VECTORIZATION_FAILED,
                "向量化知识库失败: " + e.getMessage());
        }
    }

    private KnowledgeBaseEntity loadKnowledgeBase(Long knowledgeBaseId) {
        if (knowledgeBaseRepository == null) {
            KnowledgeBaseEntity fallback = new KnowledgeBaseEntity();
            fallback.setId(knowledgeBaseId);
            return fallback;
        }
        return knowledgeBaseRepository.findById(knowledgeBaseId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在"));
    }

    private void applyPendingMetadata(List<Document> chunks, KnowledgeBaseEntity knowledgeBase, String jobId) {
        Long knowledgeBaseId = knowledgeBase.getId();
        String pendingKbId = TEMP_KB_ID_PREFIX + knowledgeBaseId + ":" + jobId;
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            chunk.getMetadata().put(METADATA_KB_ID, pendingKbId);
            chunk.getMetadata().put(METADATA_TARGET_KB_ID, knowledgeBaseId.toString());
            chunk.getMetadata().put(METADATA_VECTOR_JOB_ID, jobId);
            chunk.getMetadata().put(METADATA_CHUNK_INDEX, i + 1);
            putIfPresent(chunk, METADATA_USER_ID, knowledgeBase.getUserId());
            putIfPresent(chunk, METADATA_KB_NAME, knowledgeBase.getName());
            putIfPresent(chunk, METADATA_KB_CATEGORY, knowledgeBase.getCategory());
            putIfPresent(chunk, METADATA_ORIGINAL_FILENAME, knowledgeBase.getOriginalFilename());
        }
    }

    private void putIfPresent(Document chunk, String key, Object value) {
        if (value != null && !value.toString().isBlank()) {
            chunk.getMetadata().put(key, value.toString());
        }
    }
    
    /**
     * 基于多个知识库进行相似度搜索
     * 
     * @param query 查询文本
     * @param knowledgeBaseIds 知识库ID列表（如果为空则搜索所有）
     * @param topK 返回top K个结果
     * @return 相关文档列表
     */
    public List<Document> similaritySearch(String query, List<Long> knowledgeBaseIds, int topK, double minScore) {
        return similaritySearch(query, knowledgeBaseIds, topK, minScore, null);
    }

    public List<Document> similaritySearch(
        String query,
        List<Long> knowledgeBaseIds,
        int topK,
        double minScore,
        RagSearchFilter filter
    ) {
        log.info("向量相似度搜索: query={}, kbIds={}, topK={}, minScore={}",
            query, knowledgeBaseIds, topK, minScore);
        
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(Math.max(topK, 1));

            if (minScore > 0) {
                builder.similarityThreshold(minScore);
            }

            if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                builder.filterExpression(buildFilterExpression(knowledgeBaseIds, filter));
            }

            List<Document> results = vectorStore.similaritySearch(builder.build());
            if (results == null) {
                return List.of();
            }

            // Apply topK limiting in case VectorStore returns more than requested
            List<Document> limitedResults = results.stream()
                .filter(doc -> matchesFilter(doc, knowledgeBaseIds, filter))
                .limit(topK)
                .collect(Collectors.toList());

            log.info("搜索完成: 找到 {} 个相关文档", limitedResults.size());
            return limitedResults;
            
        } catch (Exception e) {
            log.warn("向量搜索前置过滤失败，回退到本地过滤: {}", e.getMessage());
            return similaritySearchFallback(query, knowledgeBaseIds, topK, minScore, filter);
        }
    }

    private List<Document> similaritySearchFallback(
        String query,
        List<Long> knowledgeBaseIds,
        int topK,
        double minScore,
        RagSearchFilter filter
    ) {
        try {
            // 回退检索仍保留 topK/minScore，避免兜底路径引入过多弱相关命中
            SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(Math.max(topK * 3, topK));
            if (minScore > 0) {
                builder.similarityThreshold(minScore);
            }

            List<Document> allResults = vectorStore.similaritySearch(builder.build());
            if (allResults == null || allResults.isEmpty()) {
                return List.of();
            }

            if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                allResults = allResults.stream()
                    .filter(doc -> matchesFilter(doc, knowledgeBaseIds, filter))
                    .collect(Collectors.toList());
            }

            List<Document> results = allResults.stream()
                .limit(topK)
                .collect(Collectors.toList());

            log.info("回退检索完成: 找到 {} 个相关文档", results.size());
            return results;
        } catch (Exception e) {
            log.error("向量搜索失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_QUERY_FAILED,
                "向量搜索失败: " + e.getMessage());
        }
    }

    private boolean isDocInKnowledgeBases(Document doc, List<Long> knowledgeBaseIds) {
        Object kbId = doc.getMetadata().get(METADATA_KB_ID);
        if (kbId == null) {
            return false;
        }
        try {
            Long kbIdLong = kbId instanceof Long
                ? (Long) kbId
                : Long.parseLong(kbId.toString());
            return knowledgeBaseIds.contains(kbIdLong);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean matchesFilter(Document doc, List<Long> knowledgeBaseIds, RagSearchFilter filter) {
        if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty() && !isDocInKnowledgeBases(doc, knowledgeBaseIds)) {
            return false;
        }
        if (filter == null) {
            return true;
        }
        if (filter.userId() != null && !metadataEquals(doc, METADATA_USER_ID, filter.userId().toString())) {
            return false;
        }
        if (!filter.categories().isEmpty()) {
            String category = metadataString(doc, METADATA_KB_CATEGORY);
            if (category == null || filter.categories().stream().noneMatch(category::equals)) {
                return false;
            }
        }
        if (filter.keyword() != null) {
            String keyword = filter.keyword().toLowerCase(Locale.ROOT);
            return containsIgnoreCase(doc.getText(), keyword)
                || containsIgnoreCase(metadataString(doc, METADATA_KB_NAME), keyword)
                || containsIgnoreCase(metadataString(doc, METADATA_ORIGINAL_FILENAME), keyword)
                || containsIgnoreCase(metadataString(doc, METADATA_KB_CATEGORY), keyword);
        }
        return true;
    }

    private boolean metadataEquals(Document doc, String key, String expected) {
        String actual = metadataString(doc, key);
        return expected.equals(actual);
    }

    private String metadataString(Document doc, String key) {
        Object value = doc.getMetadata().get(key);
        return value == null ? null : value.toString();
    }

    private boolean containsIgnoreCase(String text, String lowercaseKeyword) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowercaseKeyword);
    }

    private String buildFilterExpression(List<Long> knowledgeBaseIds, RagSearchFilter filter) {
        List<String> expressions = new java.util.ArrayList<>();
        expressions.add(buildKbFilterExpression(knowledgeBaseIds));
        if (filter != null && filter.userId() != null) {
            expressions.add(METADATA_USER_ID + " == '" + escapeFilterValue(filter.userId().toString()) + "'");
        }
        if (filter != null && !filter.categories().isEmpty()) {
            String values = filter.categories().stream()
                .map(this::escapeFilterValue)
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(", "));
            expressions.add(METADATA_KB_CATEGORY + " in [" + values + "]");
        }
        return String.join(" && ", expressions);
    }

    private String buildKbFilterExpression(List<Long> knowledgeBaseIds) {
        String values = knowledgeBaseIds.stream()
            .filter(Objects::nonNull)
            .map(String::valueOf)
            .map(id -> "'" + escapeFilterValue(id) + "'")
            .collect(Collectors.joining(", "));
        return METADATA_KB_ID + " in [" + values + "]";
    }

    private String escapeFilterValue(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
    
    /**
     * 删除指定知识库的所有向量数据
     * 委托给 VectorRepository 处理
     * 
     * @param knowledgeBaseId 知识库ID
     */
    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        try {
            deleteByKnowledgeBaseIdStrict(knowledgeBaseId);
        } catch (Exception e) {
            log.error("删除向量数据失败: kbId={}, error={}", knowledgeBaseId, e.getMessage(), e);
            // 不抛出异常，允许继续执行其他删除操作
            // 如果确实需要严格保证，可以取消下面的注释
            // throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_DELETE_FAILED, "删除向量数据失败");
        }
    }

    private void deleteByKnowledgeBaseIdStrict(Long knowledgeBaseId) {
        runVectorRepositoryMutation(() -> vectorRepository.deleteByKnowledgeBaseId(knowledgeBaseId));
    }

    private void activateVectorJob(Long knowledgeBaseId, String jobId) {
        runVectorRepositoryMutation(() -> {
            vectorRepository.deleteByKnowledgeBaseId(knowledgeBaseId);
            vectorRepository.promoteVectorJob(knowledgeBaseId, jobId);
        });
    }

    private void cleanupPendingVectorJob(Long knowledgeBaseId, String jobId) {
        if (jobId == null) {
            return;
        }
        try {
            runVectorRepositoryMutation(() -> vectorRepository.deleteByVectorJobId(jobId));
        } catch (Exception cleanupError) {
            log.warn("清理临时向量数据失败，可后续按 jobId 补偿: kbId={}, jobId={}, error={}",
                knowledgeBaseId, jobId, cleanupError.getMessage(), cleanupError);
        }
    }

    private void runVectorRepositoryMutation(Runnable action) {
        if (transactionalExecutor == null) {
            action.run();
            return;
        }
        transactionalExecutor.run(action);
    }
}
