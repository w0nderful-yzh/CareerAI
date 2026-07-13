package com.yzh666.careerai.modules.user.service;

import com.yzh666.careerai.modules.user.model.AuthenticatedUser;
import java.util.Optional;

public interface AccessTokenParser {

  Optional<AuthenticatedUser> parseAccessToken(String token);
}
