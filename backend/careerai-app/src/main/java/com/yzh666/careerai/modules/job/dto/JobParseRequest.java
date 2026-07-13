package com.yzh666.careerai.modules.job.dto;

import jakarta.validation.constraints.NotBlank;

public record JobParseRequest(
    @NotBlank(message = "JD 内容不能为空")
    String jdText
) {
}
