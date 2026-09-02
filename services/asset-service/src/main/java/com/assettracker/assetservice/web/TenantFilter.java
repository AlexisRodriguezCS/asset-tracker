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
 * Loads {@link TenantContext} from the gateway's {@code X-Client-Ids} header. Present (even empty)
 * means the request was authenticated and is tenant-scoped; absent means a public read or a
 * service-to-service call, which is left unscoped.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantFilter implements Filter {

  public static final String HEADER = "X-Client-Ids";

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String header = ((HttpServletRequest) request).getHeader(HEADER);
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
    try {
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }
}
