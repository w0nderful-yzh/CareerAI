package com.yzh666.careerai.modules.resumeplan.dto;

import java.util.List;

/** 可被前端跟踪、并可用于后续模拟面试验证的岗位准备任务。 */
public record PreparationTaskDTO(
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
