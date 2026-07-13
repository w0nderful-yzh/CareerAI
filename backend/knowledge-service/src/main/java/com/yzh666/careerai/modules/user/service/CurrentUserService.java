package com.yzh666.careerai.modules.user.service;

import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.modules.user.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

  private final AuthProperties authProperties;

  public Long currentUserId() {
    if (CurrentUserContext.hasInvalidToken()) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
    }

    return CurrentUserContext.get()
        .map(user -> user.userId())
        .orElseGet(this::anonymousUserId);
  }

  private Long anonymousUserId() {
    if (!authProperties.getAnonymousUser().isEnabled()) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    return authProperties.getAnonymousUser().getId();
  }
}
