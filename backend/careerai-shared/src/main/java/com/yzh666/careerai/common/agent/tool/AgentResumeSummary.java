package com.yzh666.careerai.common.agent.tool;

import java.time.LocalDateTime;

/** Agent 选择简历时使用的精简视图，避免把存储地址等内部字段暴露给模型。 */
public record AgentResumeSummary(
    Long id,
    String filename,
    Integer latestScore,
    String analyzeStatus,
    LocalDateTime uploadedAt
) {
}
