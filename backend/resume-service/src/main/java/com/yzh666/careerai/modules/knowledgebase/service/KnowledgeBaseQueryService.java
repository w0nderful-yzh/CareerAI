package com.yzh666.careerai.modules.knowledgebase.service;

import com.yzh666.careerai.common.ai.LlmProviderRegistry;
import com.yzh666.careerai.common.ai.PromptSecurityConstants;
import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.modules.knowledgebase.model.QueryRequest;
import com.yzh666.careerai.modules.knowledgebase.model.QueryResponse;
import com.yzh666.careerai.modules.knowledgebase.model.RagSearchFilter;
import com.yzh666.careerai.modules.knowledgebase.model.RagSourceDTO;
import com.yzh666.careerai.modules.knowledgebase.model.RagStreamAnswer;
import com.yzh666.careerai.modules.user.service.CurrentUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 知识库查询服务
 * 基于向量搜索的RAG问答
 */
@Slf4j
@Service
public class KnowledgeBaseQueryService {
    private static final String NO_RESULT_RESPONSE = "抱歉，在选定的知识库中未检索到相关信息。请换一个更具体的关键词或补充上下文后再试。";
    private static final int STREAM_PROBE_CHARS = 120;
    private static final int MAX_REWRITE_HISTORY_CHAR = 200;

    private final LlmProviderRegistry llmProviderRegistry;
    private final KnowledgeBaseVectorService vectorService;
    private final KnowledgeBaseListService listService;
    private final KnowledgeBaseCountService countService;
    private final CurrentUserService currentUserService;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final PromptTemplate rewritePromptTemplate;
    private final boolean rewriteEnabled;
    private final int shortQueryLength;
    private final int topkShort;
    private final int topkMedium;
    private final int topkLong;
    private final double minScoreShort;
    private final double minScoreDefault;

    public KnowledgeBaseQueryService(
            LlmProviderRegistry llmProviderRegistry,
            KnowledgeBaseVectorService vectorService,
            KnowledgeBaseListService listService,
            KnowledgeBaseCountService countService,
            CurrentUserService currentUserService,
            KnowledgeBaseQueryProperties queryProperties,
            ResourceLoader resourceLoader) throws IOException {
        this.llmProviderRegistry = llmProviderRegistry;
        this.vectorService = vectorService;
        this.listService = listService;
        this.countService = countService;
        this.currentUserService = currentUserService;
        this.systemPromptTemplate = new PromptTemplate(
            resourceLoader.getResource(queryProperties.getSystemPromptPath())
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.userPromptTemplate = new PromptTemplate(
            resourceLoader.getResource(queryProperties.getUserPromptPath())
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.rewritePromptTemplate = new PromptTemplate(
            resourceLoader.getResource(queryProperties.getRewritePromptPath())
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.rewriteEnabled = queryProperties.getRewrite().isEnabled();
        this.shortQueryLength = queryProperties.getSearch().getShortQueryLength();
        this.topkShort = queryProperties.getSearch().getTopkShort();
        this.topkMedium = queryProperties.getSearch().getTopkMedium();
        this.topkLong = queryProperties.getSearch().getTopkLong();
        this.minScoreShort = queryProperties.getSearch().getMinScoreShort();
        this.minScoreDefault = queryProperties.getSearch().getMinScoreDefault();
    }

    private ChatClient getChatClient() {
        return llmProviderRegistry.getDefaultChatClient();
    }

    /**
     * 基于单个知识库回答用户问题
     *
     * @param knowledgeBaseId 知识库ID
     * @param question 用户问题
     * @return AI回答
     */
    public String answerQuestion(Long knowledgeBaseId, String question) {
        return answerQuestion(List.of(knowledgeBaseId), question);
    }

    /**
     * 基于多个知识库回答用户问题（RAG）
     *
     * @param knowledgeBaseIds 知识库ID列表
     * @param question 用户问题
     * @return AI回答
     */
    public String answerQuestion(List<Long> knowledgeBaseIds, String question) {
        return answerQuestionWithSources(knowledgeBaseIds, question, null).answer();
    }

    public RagAnswerResult answerQuestionWithSources(
        List<Long> knowledgeBaseIds,
        String question,
        RagSearchFilter filter
    ) {
        log.info("收到知识库提问: kbIds={}, question={}", knowledgeBaseIds, question);
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || normalizeQuestion(question).isBlank()) {
            return new RagAnswerResult(NO_RESULT_RESPONSE, List.of());
        }

        countService.updateQuestionCounts(knowledgeBaseIds);

        QueryContext queryContext = buildQueryContext(question, List.of());
        List<Document> relevantDocs = retrieveRelevantDocs(queryContext, knowledgeBaseIds, normalizeFilter(filter));
        List<RagSourceDTO> sources = buildSources(relevantDocs);

        if (!hasEffectiveHit(relevantDocs)) {
            return new RagAnswerResult(NO_RESULT_RESPONSE, List.of());
        }

        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(context, question);

        try {
            String answer = getChatClient().prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            answer = normalizeAnswer(answer);

            log.info("知识库问答完成: kbIds={}", knowledgeBaseIds);
            return new RagAnswerResult(answer, sources);

        } catch (Exception e) {
            log.error("知识库问答失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_QUERY_FAILED, "知识库查询失败：" + e.getMessage());
        }
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt() {
        return systemPromptTemplate.render()
            + PromptSecurityConstants.ANTI_INJECTION_INSTRUCTION;
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(String context, String question) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("context", context);
        variables.put("question", question);
        return userPromptTemplate.render(variables);
    }

    /**
     * 查询知识库并返回完整响应
     */
    public QueryResponse queryKnowledgeBase(QueryRequest request) {
        RagAnswerResult result = answerQuestionWithSources(
            request.knowledgeBaseIds(),
            request.question(),
            new RagSearchFilter(currentUserIdOrNull(), request.categories(), request.keyword())
        );

        // 获取知识库名称（多个知识库用逗号分隔）
        List<String> kbNames = listService.getKnowledgeBaseNames(request.knowledgeBaseIds());
        String kbNamesStr = String.join("、", kbNames);

        // 使用第一个知识库ID作为主要标识（兼容前端）
        Long primaryKbId = request.knowledgeBaseIds().getFirst();

        return new QueryResponse(result.answer(), primaryKbId, kbNamesStr, result.sources());
    }

    /**
     * 流式查询知识库（SSE，无上下文）
     *
     * @param knowledgeBaseIds 知识库ID列表
     * @param question 用户问题
     * @return 流式响应
     */
    public Flux<String> answerQuestionStream(List<Long> knowledgeBaseIds, String question) {
        return answerQuestionStream(knowledgeBaseIds, question, List.of());
    }

    /**
     * 流式查询知识库（SSE，支持多轮上下文）
     *
     * @param knowledgeBaseIds 知识库ID列表
     * @param question 用户问题
     * @param history 历史对话消息（可选）
     * @return 流式响应
     */
    public Flux<String> answerQuestionStream(List<Long> knowledgeBaseIds, String question, List<Message> history) {
        return answerQuestionStreamWithSources(knowledgeBaseIds, question, history).content();
    }

    public RagStreamAnswer answerQuestionStreamWithSources(
        List<Long> knowledgeBaseIds,
        String question,
        List<Message> history
    ) {
        log.info("收到知识库流式提问: kbIds={}, question={}, historySize={}", knowledgeBaseIds, question,
                history != null ? history.size() : 0);
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || normalizeQuestion(question).isBlank()) {
            return new RagStreamAnswer(Flux.just(NO_RESULT_RESPONSE), List.of());
        }

        try {
            // 1. 验证知识库是否存在并更新问题计数
            countService.updateQuestionCounts(knowledgeBaseIds);

            // 2. Query rewrite + 动态参数检索
            List<Message> effectiveHistory = sanitizeHistory(history);
            QueryContext queryContext = buildQueryContext(question, effectiveHistory);
            List<Document> relevantDocs = retrieveRelevantDocs(
                queryContext,
                knowledgeBaseIds,
                RagSearchFilter.ofUser(currentUserIdOrNull())
            );
            List<RagSourceDTO> sources = buildSources(relevantDocs);

            if (!hasEffectiveHit(relevantDocs)) {
                return new RagStreamAnswer(Flux.just(NO_RESULT_RESPONSE), List.of());
            }

            // 3. 构建上下文
            String context = relevantDocs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n---\n\n"));

            log.debug("检索到 {} 个相关文档片段", relevantDocs.size());

            // 4. 构建提示词
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(context, question);

            // 5. 流式调用（带历史上下文）+ 探测窗口归一化
            var promptSpec = getChatClient().prompt().system(systemPrompt);
            if (!effectiveHistory.isEmpty()) {
                promptSpec = promptSpec.messages(effectiveHistory);
            }
            Flux<String> responseFlux = promptSpec
                    .user(userPrompt)
                    .stream()
                    .content();

            log.info("开始流式输出知识库回答(探测窗口): kbIds={}", knowledgeBaseIds);
            Flux<String> content = normalizeStreamOutput(responseFlux)
                .doOnComplete(() -> log.info("流式输出完成: kbIds={}", knowledgeBaseIds))
                .onErrorResume(e -> {
                    log.error("流式输出失败: kbIds={}, error={}", knowledgeBaseIds, e.getMessage(), e);
                    return Flux.just("【错误】知识库查询失败：AI服务暂时不可用，请稍后重试。");
                });
            return new RagStreamAnswer(content, sources);

        } catch (Exception e) {
            log.error("知识库流式问答失败: {}", e.getMessage(), e);
            return new RagStreamAnswer(Flux.just("【错误】知识库查询失败：" + e.getMessage()), List.of());
        }
    }

    private QueryContext buildQueryContext(String originalQuestion, List<Message> history) {
        String normalizedQuestion = normalizeQuestion(originalQuestion);
        String rewrittenQuestion = rewriteQuestion(normalizedQuestion, history);
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(rewrittenQuestion);
        candidates.add(normalizedQuestion);

        SearchParams searchParams = resolveSearchParams(normalizedQuestion);
        return new QueryContext(normalizedQuestion, new ArrayList<>(candidates), searchParams);
    }

    private List<Message> sanitizeHistory(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        return history;
    }

//       清洗
    private String normalizeQuestion(String question) {
        return question == null ? "" : question.trim();
    }

//    向量检索
    private List<Document> retrieveRelevantDocs(
        QueryContext queryContext,
        List<Long> knowledgeBaseIds,
        RagSearchFilter filter
    ) {
        for (String candidateQuery : queryContext.candidateQueries()) {
            if (candidateQuery.isBlank()) {
                continue;
            }
            List<Document> docs = vectorService.similaritySearch(
                candidateQuery,
                knowledgeBaseIds,
                queryContext.searchParams().topK(),
                queryContext.searchParams().minScore(),
                filter
            );
            log.info("检索候选 query='{}'，命中 {} 条", candidateQuery, docs.size());
            if (hasEffectiveHit(docs)) {
                return docs;
            }
        }
        return List.of();
    }

    private RagSearchFilter normalizeFilter(RagSearchFilter filter) {
        if (filter == null) {
            return RagSearchFilter.ofUser(currentUserIdOrNull());
        }
        Long userId = filter.userId() != null ? filter.userId() : currentUserIdOrNull();
        return new RagSearchFilter(userId, filter.categories(), filter.keyword());
    }

    private Long currentUserIdOrNull() {
        try {
            return currentUserService.currentUserId();
        } catch (Exception e) {
            log.debug("未获取到当前用户，RAG 检索跳过 user_id 元数据过滤: {}", e.getMessage());
            return null;
        }
    }

    private List<RagSourceDTO> buildSources(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<RagSourceDTO> sources = new ArrayList<>();
        for (Document doc : docs) {
            Long knowledgeBaseId = metadataLong(doc, "kb_id");
            Integer chunkIndex = metadataInteger(doc, "chunk_index");
            String snippet = toSnippet(doc.getText());
            String key = knowledgeBaseId + ":" + chunkIndex + ":" + snippet;
            if (!seen.add(key)) {
                continue;
            }
            sources.add(new RagSourceDTO(
                knowledgeBaseId,
                metadataString(doc, "kb_name"),
                metadataString(doc, "kb_category"),
                metadataString(doc, "kb_original_filename"),
                chunkIndex,
                snippet,
                metadataDouble(doc, "score")
            ));
            if (sources.size() >= 5) {
                break;
            }
        }
        return sources;
    }

    private String toSnippet(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        int maxLength = 220;
        return compact.length() <= maxLength ? compact : compact.substring(0, maxLength) + "...";
    }

    private String metadataString(Document doc, String key) {
        Object value = doc.getMetadata().get(key);
        return value == null ? null : value.toString();
    }

    private Long metadataLong(Document doc, String key) {
        String value = metadataString(doc, key);
        if (value == null || value.startsWith("pending:")) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer metadataInteger(Document doc, String key) {
        String value = metadataString(doc, key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double metadataDouble(Document doc, String key) {
        Object value = doc.getMetadata().get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private SearchParams resolveSearchParams(String question) {
        int compactLength = question.replaceAll("\\s+", "").length();
        if (compactLength <= shortQueryLength) {
            return new SearchParams(topkShort, minScoreShort);
        }
        if (compactLength <= 12) {
            return new SearchParams(topkMedium, minScoreDefault);
        }
        return new SearchParams(topkLong, minScoreDefault);
    }

//    改写
    private String rewriteQuestion(String question, List<Message> history) {
        if (!rewriteEnabled || question.isBlank()) {
            return question;
        }
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("question", question);
            variables.put("history", formatHistoryForRewrite(history));
            String rewritePrompt = rewritePromptTemplate.render(variables);
            String rewritten = getChatClient().prompt()
                .user(rewritePrompt)
                .call()
                .content();
            if (rewritten == null || rewritten.isBlank()) {
                return question;
            }
            String normalized = rewritten.trim();
            log.info("Query rewrite: origin='{}', rewritten='{}', historySize={}", question, normalized, history.size());
            return normalized;
        } catch (Exception e) {
            log.warn("Query rewrite 失败，使用原问题继续检索: {}", e.getMessage());
            return question;
        }
    }

    /**
     * 将历史消息格式化为重写 prompt 中的文本摘要。
     * 每条消息格式：用户: xxx / 助手: xxx
     */
    private String formatHistoryForRewrite(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Message msg : history) {
            if (msg instanceof UserMessage) {
                sb.append("用户: ").append(msg.getText()).append("\n");
            } else if (msg instanceof AssistantMessage) {
                // 截断过长的助手回复，避免 rewrite prompt 过长
                String text = msg.getText();
                if (text.length() > MAX_REWRITE_HISTORY_CHAR) {
                    text = text.substring(0, MAX_REWRITE_HISTORY_CHAR) + "...";
                }
                sb.append("助手: ").append(text).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private boolean hasEffectiveHit(List<Document> docs) {
        return docs != null && !docs.isEmpty();
    }

    private String normalizeAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return NO_RESULT_RESPONSE;
        }
        String normalized = answer.trim();
        if (isNoResultLike(normalized)) {
            return NO_RESULT_RESPONSE;
        }
        return normalized;
    }

    private boolean isNoResultLike(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("没有找到相关信息")
            || text.contains("未检索到相关信息")
            || text.contains("信息不足")
            || text.contains("超出知识库范围")
            || text.contains("无法根据提供内容回答");
    }

    /**
     * 先观察前一小段流式内容，快速识别“无信息”模板。
     * - 命中无信息：立即输出固定模板并结束，防止长篇拒答
     * - 非无信息：尽快释放缓冲并继续实时透传
     */
    private Flux<String> normalizeStreamOutput(Flux<String> rawFlux) {
        return Flux.create(sink -> {
            StringBuilder probeBuffer = new StringBuilder();
            AtomicBoolean passthrough = new AtomicBoolean(false);
            AtomicBoolean completed = new AtomicBoolean(false);
            final Disposable[] disposableRef = new Disposable[1];

            disposableRef[0] = rawFlux.subscribe(
                chunk -> {
                    if (completed.get() || sink.isCancelled()) {
                        return;
                    }
                    if (passthrough.get()) {
                        sink.next(chunk);
                        return;
                    }

                    probeBuffer.append(chunk);
                    String probeText = probeBuffer.toString();
                    if (isNoResultLike(probeText)) {
                        completed.set(true);
                        sink.next(NO_RESULT_RESPONSE);
                        sink.complete();
                        if (disposableRef[0] != null) {
                            disposableRef[0].dispose();
                        }
                        return;
                    }

                    if (probeBuffer.length() >= STREAM_PROBE_CHARS) {
                        passthrough.set(true);
                        sink.next(probeText);
                        probeBuffer.setLength(0);
                    }
                },
                sink::error,
                () -> {
                    if (completed.get() || sink.isCancelled()) {
                        return;
                    }
                    if (!passthrough.get()) {
                        sink.next(normalizeAnswer(probeBuffer.toString()));
                    }
                    sink.complete();
                }
            );

            sink.onCancel(() -> {
                if (disposableRef[0] != null) {
                    disposableRef[0].dispose();
                }
            });
        });
    }

    private record SearchParams(int topK, double minScore) {
    }

    private record QueryContext(String originalQuestion, List<String> candidateQueries, SearchParams searchParams) {
    }

    public record RagAnswerResult(String answer, List<RagSourceDTO> sources) {
    }
}
