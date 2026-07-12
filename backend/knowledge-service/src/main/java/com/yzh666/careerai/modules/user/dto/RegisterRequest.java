package com.yzh666.careerai.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度需为 3-64 个字符")
    String username,

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 72, message = "密码长度需为 6-72 个字符")
    String password,

    @Size(max = 64, message = "昵称最多 64 个字符")
    String displayName
) {
}
