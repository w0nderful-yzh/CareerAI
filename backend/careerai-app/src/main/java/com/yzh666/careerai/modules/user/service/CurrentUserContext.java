package com.yzh666.careerai.modules.user.service;

import com.yzh666.careerai.modules.user.model.AuthenticatedUser;
import java.util.Optional;

public final class CurrentUserContext {

    private static final ThreadLocal<AuthenticatedUser> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> INVALID_TOKEN = ThreadLocal.withInitial(() -> false);

    private CurrentUserContext() {
    }

    public static void set(AuthenticatedUser user) {
        CURRENT_USER.set(user);
        INVALID_TOKEN.set(false);
    }

    public static Optional<AuthenticatedUser> get() {
        return Optional.ofNullable(CURRENT_USER.get());
    }

    public static void markInvalidToken() {
        CURRENT_USER.remove();
        INVALID_TOKEN.set(true);
    }

    public static boolean hasInvalidToken() {
        return Boolean.TRUE.equals(INVALID_TOKEN.get());
    }

    public static void clear() {
        CURRENT_USER.remove();
        INVALID_TOKEN.remove();
    }
}
