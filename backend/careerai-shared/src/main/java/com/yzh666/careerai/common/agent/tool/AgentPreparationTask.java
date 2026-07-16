package com.yzh666.careerai.common.agent.tool;

import java.util.List;

/** Agent 创建并由 Java 持久化的结构化岗位准备任务。 */
public record AgentPreparationTask(
    String id,
    String category,
    String title,
    String priority,
    Integer suggestedDays,
    String verificationMethod,
    String status,
    List<String> relatedRequirementIds
) {
}
