package com.assettracker.apigateway.web;

import com.assettracker.apigateway.ratelimit.RateLimitStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A fixed-window rate limit on the credential endpoints ({@code POST /api/auth/**}, except {@code
 * /validate}) keyed by client IP, so brute-forcing login / register / the Microsoft exchange costs
 * something.
 *
 * <p>Where the counters live is a deployment decision, so this filter does not own them: see {@link
 * RateLimitStore}. With Redis configured the window is cluster-wide; without it each gateway counts
 * alone, which for more than one replica is a speed bump rather than a limit.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthRateLimitFilter extends OncePerRequestFilter {

  static final int DEFAULT_MAX_PER_WINDOW = 10;
  static final long DEFAULT_WINDOW_MS = 60_000L;

  private static final long MILLIS_PER_SECOND = 1_000L;

  private final RateLimitStore store;
  private final Counter rejected;
  private final int maxPerWindow;
  private final long windowMs;
  private final String retryAfterSeconds;

  public AuthRateLimitFilter(
      RateLimitStore store,
      MeterRegistry meters,
      @Value("${security.rate-limit.max-per-window:10}") int maxPerWindow,
      @Value("${security.rate-limit.window-ms:60000}") long windowMs) {
    this.store = store;
    this.rejected = meters.counter("assettracker.auth.rate_limited");
    this.maxPerWindow = maxPerWindow;
    this.windowMs = windowMs;
    this.retryAfterSeconds = String.valueOf(windowMs / MILLIS_PER_SECOND);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    if (!rateLimited(request)) {
      chain.doFilter(request, response);
      return;
    }

    if (store.tryAcquire(clientKey(request), maxPerWindow, windowMs)) {
      chain.doFilter(request, response);
      return;
    }

    rejected.increment();
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader("Retry-After", retryAfterSeconds);
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
}
