package com.assettracker.apigateway.web;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A small in-memory fixed-window rate limit on the credential endpoints ({@code POST /api/auth/**},
 * except {@code /validate}) keyed by client IP, so brute-forcing login / register / the Microsoft
 * exchange costs something. Single-instance only - a real deployment moves this to a shared store
 * (Redis) behind Spring Cloud Gateway's {@code RequestRateLimiter}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthRateLimitFilter extends OncePerRequestFilter {

  static final int MAX_PER_WINDOW = 10;
  static final long WINDOW_MS = 60_000L;
  private static final String RETRY_AFTER_SECONDS = "60";

  private final Map<String, Window> windows = new ConcurrentHashMap<>();
  private final Counter rejected;

  public AuthRateLimitFilter(MeterRegistry meters) {
    this.rejected = meters.counter("assettracker.auth.rate_limited");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    if (!rateLimited(request)) {
      chain.doFilter(request, response);
      return;
    }

    Window window = windows.computeIfAbsent(clientKey(request), k -> new Window());
    if (window.tryAcquire()) {
      chain.doFilter(request, response);
      return;
    }

    rejected.increment();
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader("Retry-After", RETRY_AFTER_SECONDS);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response
        .getWriter()
        .write("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many attempts; slow down.\"}");
  }

  private static boolean rateLimited(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return "POST".equalsIgnoreCase(request.getMethod())
        && uri.startsWith("/api/auth/")
        && !uri.equals("/api/auth/validate");
  }

  private static String clientKey(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    return forwarded != null && !forwarded.isBlank()
        ? forwarded.split(",")[0].trim()
        : request.getRemoteAddr();
  }

  /** One caller's fixed-window counter. */
  private static final class Window {
    private long windowStart = System.currentTimeMillis();
    private int count;

    synchronized boolean tryAcquire() {
      long now = System.currentTimeMillis();
      if (now - windowStart >= WINDOW_MS) {
        windowStart = now;
        count = 0;
      }
      if (count >= MAX_PER_WINDOW) {
        return false;
      }
      count++;
      return true;
    }
  }
}
