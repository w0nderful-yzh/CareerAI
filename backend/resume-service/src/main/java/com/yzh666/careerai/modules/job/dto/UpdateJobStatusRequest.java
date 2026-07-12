package com.yzh666.careerai.modules.job.dto;

import com.yzh666.careerai.modules.job.model.JobStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateJobStatusRequest(
    @NotNull(message = "岗位状态不能为空")
    JobStatus status
) {
}
