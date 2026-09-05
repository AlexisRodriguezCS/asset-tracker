package com.assettracker.apigateway.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * After the JWT is validated, forwards the caller's identity to the downstream service as headers
 * so services can scope and audit without re-parsing the token: {@code X-User-Id} (subject), {@code
 * X-User-Role}, {@code X-Client-Ids} (comma-separated). Registered as a plain component filter, so
 * it runs after Spring Security has populated the context. Any client-supplied value for these
 * headers is overridden (to the real value, or to absent) so they cannot be spoofed.
 */
@Component
public class IdentityHeaderFilter extends OncePerRequestFilter {

  static final String USER_ID = "X-User-Id";
  static final String USER_ROLE = "X-User-Role";
  static final String CLIENT_IDS = "X-Client-Ids";
  static final String PERSON_ID = "X-Person-Id";
  static final String CORRELATION_ID = "X-Correlation-Id";
  static final String MDC_KEY = "correlationId";

  private static final int SHORT_ID_LENGTH = 12;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    Map<String, String> injected = new HashMap<>();
    injected.put(USER_ID, null);
    injected.put(USER_ROLE, null);
    injected.put(CLIENT_IDS, null);
    injected.put(PERSON_ID, null);

    // Correlation id: accept the client's if present (unlike the identity headers, which are always
    // overridden to defeat spoofing), generate one otherwise. Forwarded downstream and logged here.
    String correlationId = request.getHeader(CORRELATION_ID);
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UUID.randomUUID().toString().substring(0, SHORT_ID_LENGTH);
    }
    injected.put(CORRELATION_ID, correlationId);
    response.setHeader(CORRELATION_ID, correlationId);
    MDC.put(MDC_KEY, correlationId);

    if (SecurityContextHolder.getContext().getAuthentication()
        instanceof JwtAuthenticationToken auth) {
      Jwt jwt = auth.getToken();
      injected.put(USER_ID, jwt.getSubject());
      injected.put(USER_ROLE, jwt.getClaimAsString("role"));
      Object personId = jwt.getClaim("personId");
      if (personId != null) {
        injected.put(PERSON_ID, String.valueOf(personId));
      }
      Object claim = jwt.getClaim("clientIds");
      if (claim instanceof Iterable<?> ids) {
        injected.put(
            CLIENT_IDS,
            StreamSupport.stream(ids.spliterator(), false)
                .map(String::valueOf)
                .collect(Collectors.joining(",")));
      }
    }

    try {
      chain.doFilter(new IdentityRequest(request, injected), response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  /** Wraps the request so the injected identity headers win over anything the client sent. */
  private static final class IdentityRequest extends HttpServletRequestWrapper {

    private final Map<String, String> overrides;

    IdentityRequest(HttpServletRequest request, Map<String, String> overrides) {
      super(request);
      this.overrides = overrides;
    }

    @Override
    public String getHeader(String name) {
      String key = normalise(name);
      return overrides.containsKey(key) ? overrides.get(key) : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      String key = normalise(name);
      if (!overrides.containsKey(key)) {
        return super.getHeaders(name);
      }
      String value = overrides.get(key);
      return value == null
          ? Collections.emptyEnumeration()
          : Collections.enumeration(List.of(value));
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      // The gateway copies downstream headers by iterating getHeaderNames(); the
      // injected ones must appear here (and the client's originals must not).
      List<String> names = new java.util.ArrayList<>();
      Enumeration<String> original = super.getHeaderNames();
      while (original.hasMoreElements()) {
        String n = original.nextElement();
        if (!overrides.containsKey(normalise(n))) {
          names.add(n);
        }
      }
      overrides.forEach(
          (k, v) -> {
            if (v != null) {
              names.add(k);
            }
          });
      return Collections.enumeration(names);
    }

    private static String normalise(String name) {
      return switch (name.toLowerCase()) {
        case "x-user-id" -> USER_ID;
        case "x-user-role" -> USER_ROLE;
        case "x-client-ids" -> CLIENT_IDS;
        case "x-person-id" -> PERSON_ID;
        case "x-correlation-id" -> CORRELATION_ID;
        default -> name;
      };
    }
  }
}
