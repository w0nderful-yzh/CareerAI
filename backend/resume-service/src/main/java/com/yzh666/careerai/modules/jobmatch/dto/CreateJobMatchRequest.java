package com.yzh666.careerai.modules.jobmatch.dto;

import jakarta.validation.constraints.NotNull;

public record CreateJobMatchRequest(
    @NotNull(message = "请选择简历")
    Long resumeId,

    @NotNull(message = "请选择岗位")
    Long jobId
) {
}
