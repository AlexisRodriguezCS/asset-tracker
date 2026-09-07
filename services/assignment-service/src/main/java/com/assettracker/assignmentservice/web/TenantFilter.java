package com.assettracker.assignmentservice.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Loads {@link TenantContext} and {@link CallerContext} from the claims of the token this service
 * verified.
 *
 * <p>It used to read them from the {@code X-Client-Ids} / {@code X-User-Role} headers the gateway
 * forwards, which meant the answer to "who is this and what may they see" was whatever the caller
 * wrote in a header. Anything able to reach this port could read a whole tenant by asserting a role
 * - or, because a missing role was taken for a trusted internal call, by sending nothing at all.
 *
 * <p>The headers are still forwarded and still useful for logs and the audit trail. They are no
 * longer what authorization is decided on.
 *
 * <p>No {@code @Order}: this must run after Spring Security has validated the token and populated
 * the context, and the default order for a component filter does exactly that. An early order here
 * would silently see no authentication and scope every request to nothing.
 */
@Component
public class TenantFilter implements Filter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    Jwt token = currentToken();
    if (token != null) {
      TenantContext.set(clientIds(token));
      CallerContext.set(token.getClaimAsString("role"), personId(token));
    }
    try {
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
      CallerContext.clear();
    }
  }

  private static Jwt currentToken() {
    return SecurityContextHolder.getContext().getAuthentication()
            instanceof JwtAuthenticationToken authenticated
        ? authenticated.getToken()
        : null;
  }

  /**
   * An authenticated caller with no {@code clientIds} claim is scoped to nothing, not everything.
   */
  private static Set<Long> clientIds(Jwt token) {
    // read as a raw claim: the token carries numbers, and asking for them as strings goes
    // through a converter that need not agree with the JSON it was given
    if (!(token.getClaim("clientIds") instanceof List<?> values)) {
      return Set.of();
    }
    return values.stream()
        .map(String::valueOf)
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .map(Long::valueOf)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static Long personId(Jwt token) {
    Object claim = token.getClaim("personId");
    if (claim == null) {
      return null;
    }
    try {
      return Long.valueOf(String.valueOf(claim).trim());
    } catch (NumberFormatException notANumber) {
      return null;
    }
  }
}
