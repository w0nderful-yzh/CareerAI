package com.yzh666.careerai.modules.user.controller;

import com.yzh666.careerai.common.result.Result;
import com.yzh666.careerai.modules.user.dto.AuthResponse;
import com.yzh666.careerai.modules.user.dto.CurrentUserDTO;
import com.yzh666.careerai.modules.user.dto.LoginRequest;
import com.yzh666.careerai.modules.user.dto.RegisterRequest;
import com.yzh666.careerai.modules.user.service.AuthService;
import com.yzh666.careerai.modules.user.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    @PostMapping("/api/auth/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/api/auth/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @GetMapping("/api/auth/me")
    public Result<CurrentUserDTO> me() {
        return Result.success(currentUserService.currentUserDTO());
    }
}
