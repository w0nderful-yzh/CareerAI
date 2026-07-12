package com.yzh666.careerai.modules.user.service;

import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.modules.user.config.AuthProperties;
import com.yzh666.careerai.modules.user.dto.CurrentUserDTO;
import com.yzh666.careerai.modules.user.model.AuthenticatedUser;
import com.yzh666.careerai.modules.user.model.UserEntity;
import com.yzh666.careerai.modules.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;
    private final PasswordHashService passwordHashService;
    private final AuthProperties authProperties;

    @Transactional
    public Long currentUserId() {
        return currentUser().getId();
    }

    @Transactional
    public CurrentUserDTO currentUserDTO() {
        UserEntity user = currentUser();
        boolean anonymous = authProperties.getAnonymousUser().getUsername().equals(user.getUsername());
        return new CurrentUserDTO(user.getId(), user.getUsername(), user.getDisplayName(), anonymous);
    }

    private UserEntity currentUser() {
        if (CurrentUserContext.hasInvalidToken()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }

        return CurrentUserContext.get()
            .map(this::loadAuthenticatedUser)
            .orElseGet(this::getOrCreateAnonymousUser);
    }

    private UserEntity loadAuthenticatedUser(AuthenticatedUser authenticatedUser) {
        return userRepository.findById(authenticatedUser.userId())
            .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录"));
    }

    private UserEntity getOrCreateAnonymousUser() {
        if (!authProperties.getAnonymousUser().isEnabled()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }

        String username = authProperties.getAnonymousUser().getUsername();
        return userRepository.findByUsername(username)
            .orElseGet(() -> {
                UserEntity user = new UserEntity();
                user.setUsername(username);
                user.setDisplayName("本地开发用户");
                user.setPasswordHash(passwordHashService.hash(UUID.randomUUID().toString()));
                user.setEnabled(true);
                return userRepository.save(user);
            });
    }
}
