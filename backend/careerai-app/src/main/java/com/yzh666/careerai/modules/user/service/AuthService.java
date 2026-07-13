package com.yzh666.careerai.modules.user.service;

import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.modules.user.dto.AuthResponse;
import com.yzh666.careerai.modules.user.dto.CurrentUserDTO;
import com.yzh666.careerai.modules.user.dto.LoginRequest;
import com.yzh666.careerai.modules.user.dto.RegisterRequest;
import com.yzh666.careerai.modules.user.model.UserEntity;
import com.yzh666.careerai.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordHashService passwordHashService;
    private final JwtTokenService jwtTokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名已存在");
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setDisplayName(normalizeDisplayName(request.displayName(), username));
        user.setPasswordHash(passwordHashService.hash(request.password()));
        user.setEnabled(true);

        return toAuthResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String username = normalizeUsername(request.username());
        UserEntity user = userRepository.findByUsername(username)
            .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));

        if (!passwordHashService.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(UserEntity user) {
        String token = jwtTokenService.createAccessToken(user);
        CurrentUserDTO userDTO = new CurrentUserDTO(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            false
        );
        return new AuthResponse(token, "Bearer", jwtTokenService.getExpiresInSeconds(), userDTO);
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private String normalizeDisplayName(String displayName, String username) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return username;
        }
        return displayName.trim();
    }
}
