package com.yzh666.careerai.modules.resumeplan.dto;

import jakarta.validation.constraints.NotNull;

public record CreateResumeImprovementPlanRequest(
    @NotNull(message = "匹配报告ID不能为空")
    Long matchReportId
) {
}
