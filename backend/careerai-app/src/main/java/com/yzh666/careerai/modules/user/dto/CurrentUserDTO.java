package com.yzh666.careerai.modules.user.dto;

public record CurrentUserDTO(
    Long id,
    String username,
    String displayName,
    boolean anonymous
) {
}
