package com.yzh666.careerai.modules.user.web;

import com.yzh666.careerai.modules.user.service.AccessTokenParser;
import com.yzh666.careerai.modules.user.service.CurrentUserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final AccessTokenParser accessTokenParser;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    try {
      String authorization = request.getHeader("Authorization");
      if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        accessTokenParser.parseAccessToken(token)
            .ifPresentOrElse(CurrentUserContext::set, CurrentUserContext::markInvalidToken);
      }
      filterChain.doFilter(request, response);
    } finally {
      CurrentUserContext.clear();
    }
  }
}
