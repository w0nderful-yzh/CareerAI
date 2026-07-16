package com.yzh666.careerai.common.agent.tool;

import java.time.LocalDateTime;

/** 可轮询的岗位匹配任务状态；完成后 report 会随任务一起返回。 */
public record AgentJobMatchTask(
    Long id,
    String status,
    Long resumeId,
    Long jobId,
    Long reportId,
    String errorMessage,
    AgentJobMatchReport report,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
