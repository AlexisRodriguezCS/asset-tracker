package com.assettracker.peopleservice.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Puts a correlation id in the logging {@link MDC} for the life of each request so one user action
 * can be grepped across every service. Honours an inbound {@code X-Correlation-Id} (the gateway
 * sets one on every proxied call), generates a short id when the service is hit directly, and
 * echoes it on the response.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements Filter {

  public static final String HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "correlationId";

  private static final int SHORT_ID_LENGTH = 12;

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String id = ((HttpServletRequest) request).getHeader(HEADER);
    if (id == null || id.isBlank()) {
      id = UUID.randomUUID().toString().substring(0, SHORT_ID_LENGTH);
    }
    MDC.put(MDC_KEY, id);
    ((HttpServletResponse) response).setHeader(HEADER, id);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }
}
