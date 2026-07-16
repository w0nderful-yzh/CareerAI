package com.yzh666.careerai.common.agent.tool;

import java.time.LocalDateTime;

/** Agent 执行岗位分析时使用的稳定岗位快照。 */
public record AgentJobSnapshot(
    Long id,
    String title,
    String company,
    String location,
    String status,
    String jdText,
    LocalDateTime updatedAt
) {
}
