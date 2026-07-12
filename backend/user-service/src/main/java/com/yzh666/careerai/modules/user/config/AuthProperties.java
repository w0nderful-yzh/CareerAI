package com.yzh666.careerai.modules.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private String jwtSecret = "careerai-dev-jwt-secret-change-me-at-least-32";
    private long accessTokenTtlMinutes = 120;
    private AnonymousUser anonymousUser = new AnonymousUser();

    @Data
    public static class AnonymousUser {
        private boolean enabled = true;
        private String username = "local-dev";
    }
}
