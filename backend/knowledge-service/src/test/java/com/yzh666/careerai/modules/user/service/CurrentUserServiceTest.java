package com.yzh666.careerai.modules.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.modules.user.config.AuthProperties;
import com.yzh666.careerai.modules.user.model.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CurrentUserServiceTest {

  private final AuthProperties properties = new AuthProperties();
  private final CurrentUserService service = new CurrentUserService(properties);

  @AfterEach
  void clearContext() {
    CurrentUserContext.clear();
  }

  @Test
  void returnsAuthenticatedSubjectWithoutDatabaseLookup() {
    CurrentUserContext.set(new AuthenticatedUser(42L, "alice"));

    assertEquals(42L, service.currentUserId());
  }

  @Test
  void usesConfiguredAnonymousIdForLocalDevelopment() {
    properties.getAnonymousUser().setId(7L);

    assertEquals(7L, service.currentUserId());
  }

  @Test
  void rejectsMissingAuthenticationWhenAnonymousAccessIsDisabled() {
    properties.getAnonymousUser().setEnabled(false);

    BusinessException exception = assertThrows(BusinessException.class, service::currentUserId);
    assertEquals(401, exception.getCode());
  }

  @Test
  void rejectsInvalidTokenInsteadOfFallingBackToAnonymousUser() {
    CurrentUserContext.markInvalidToken();

    BusinessException exception = assertThrows(BusinessException.class, service::currentUserId);
    assertEquals(401, exception.getCode());
  }
}
