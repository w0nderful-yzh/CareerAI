package com.yzh666.careerai.modules.knowledgebase.service;

import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.yzh666.careerai.modules.knowledgebase.repository.KnowledgeBaseRepository;
import com.yzh666.careerai.modules.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 知识库计数服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseCountService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final CurrentUserService currentUserService;

    /**
     * 批量更新知识库提问计数（使用单条 SQL 批量更新）
     * 每个知识库的 questionCount +1，表示该知识库参与回答的次数
     *
     * @param knowledgeBaseIds 知识库ID列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateQuestionCounts(List<Long> knowledgeBaseIds) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return;
        }

        // 去重
        List<Long> uniqueIds = knowledgeBaseIds.stream().distinct().toList();
        Long userId = currentUserService.currentUserId();

        // 验证所有知识库是否存在
        Set<Long> existingIds = new HashSet<>(knowledgeBaseRepository.findByIdInAndUserId(uniqueIds, userId)
                .stream().map(KnowledgeBaseEntity::getId).toList());

        for (Long id : uniqueIds) {
            if (!existingIds.contains(id)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在: " + id);
            }
        }

        // 批量更新（单条 SQL）
        int updated = knowledgeBaseRepository.incrementQuestionCountBatchForUser(userId, uniqueIds);
        log.debug("批量更新知识库提问计数: ids={}, updated={}", uniqueIds, updated);
    }
}
