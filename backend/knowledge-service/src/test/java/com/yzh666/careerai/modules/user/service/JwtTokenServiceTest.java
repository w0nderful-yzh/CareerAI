package com.yzh666.careerai.modules.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yzh666.careerai.modules.user.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class JwtTokenServiceTest {

  private static final String SECRET = "test-secret-that-is-long-enough-for-hmac";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JwtTokenService service = createService();

  @Test
  void parsesValidTokenIssuedByMainApplication() throws Exception {
    String token = token(42L, "alice", Instant.now().plusSeconds(60).getEpochSecond());

    var user = service.parseAccessToken(token).orElseThrow();
    assertEquals(42L, user.userId());
    assertEquals("alice", user.username());
  }

  @Test
  void rejectsExpiredOrTamperedToken() throws Exception {
    String expired = token(42L, "alice", Instant.now().minusSeconds(1).getEpochSecond());
    String valid = token(42L, "alice", Instant.now().plusSeconds(60).getEpochSecond());

    assertTrue(service.parseAccessToken(expired).isEmpty());
    assertTrue(service.parseAccessToken(valid + "x").isEmpty());
  }

  private JwtTokenService createService() {
    AuthProperties properties = new AuthProperties();
    properties.setJwtSecret(SECRET);
    return new JwtTokenService(properties, objectMapper);
  }

  private String token(Long userId, String username, long expiresAt) throws Exception {
    String header = encode(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
    String payload = encode(objectMapper.writeValueAsBytes(Map.of(
        "sub", userId,
        "username", username,
        "exp", expiresAt
    )));
    String signingInput = header + "." + payload;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return signingInput + "." + encode(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
  }

  private String encode(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }
}
