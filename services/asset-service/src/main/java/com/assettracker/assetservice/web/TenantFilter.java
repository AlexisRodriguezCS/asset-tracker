package com.assettracker.assetservice.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Loads {@link TenantContext} and {@link CallerContext} from the identity headers the gateway
 * forwards. A present {@code X-Client-Ids} (even empty) means the request was authenticated and is
 * tenant-scoped; absent means a service-to-service call, which is left unscoped.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantFilter implements Filter {

  public static final String HEADER = "X-Client-Ids";
  public static final String ROLE_HEADER = "X-User-Role";
  public static final String PERSON_HEADER = "X-Person-Id";

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest http = (HttpServletRequest) request;
    String header = http.getHeader(HEADER);
    if (header != null) {
      Set<Long> ids =
          header.isBlank()
              ? Set.of()
              : Arrays.stream(header.split(","))
                  .map(String::trim)
                  .filter(s -> !s.isEmpty())
                  .map(Long::valueOf)
                  .collect(Collectors.toUnmodifiableSet());
      TenantContext.set(ids);
    }
    CallerContext.set(http.getHeader(ROLE_HEADER), parseId(http.getHeader(PERSON_HEADER)));
    try {
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
      CallerContext.clear();
    }
  }

  private static Long parseId(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Long.valueOf(raw.trim());
    } catch (NumberFormatException notANumber) {
      return null;
    }
  }
}
