package com.yzh666.careerai.modules.user.service;

import com.yzh666.careerai.modules.user.config.AuthProperties;
import com.yzh666.careerai.modules.user.model.AuthenticatedUser;
import com.yzh666.careerai.modules.user.model.UserEntity;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class JwtTokenService implements AccessTokenParser {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    public String createAccessToken(UserEntity user) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + getExpiresInSeconds();

        try {
            String header = objectMapper.writeValueAsString(Map.of(
                "alg", "HS256",
                "typ", "JWT"
            ));
            String payload = objectMapper.writeValueAsString(Map.of(
                "sub", user.getId(),
                "username", user.getUsername(),
                "iat", issuedAt,
                "exp", expiresAt
            ));
            String signingInput = base64Url(header.getBytes(StandardCharsets.UTF_8))
                + "."
                + base64Url(payload.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + sign(signingInput);
        } catch (Exception e) {
            throw new IllegalStateException("创建访问令牌失败", e);
        }
    }

    @Override
    public Optional<AuthenticatedUser> parseAccessToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }

            String signingInput = parts[0] + "." + parts[1];
            if (!constantTimeEquals(sign(signingInput), parts[2])) {
                return Optional.empty();
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(
                new String(payloadBytes, StandardCharsets.UTF_8),
                new TypeReference<>() {
                }
            );

            long expiresAt = asLong(payload.get("exp"));
            if (expiresAt <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }

            Long userId = asLong(payload.get("sub"));
            String username = String.valueOf(payload.get("username"));
            return Optional.of(new AuthenticatedUser(userId, username));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public long getExpiresInSeconds() {
        return authProperties.getAccessTokenTtlMinutes() * 60;
    }

    private String sign(String signingInput) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(
            authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8),
            HMAC_SHA256
        ));
        return base64Url(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
