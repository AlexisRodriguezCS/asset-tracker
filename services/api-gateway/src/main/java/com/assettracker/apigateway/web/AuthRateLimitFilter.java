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
 * <p><b>The window is per gateway instance, not per cluster.</b> Counters live in this process's
 * heap, so N replicas allow roughly N x {@code security.rate-limit.max-per-window} attempts per
 * minute and a rolling restart resets every counter. That is enough to make credential stuffing
 * expensive but it is not a hard limit - the budget is therefore a property, so a deployment can
 * divide it by its replica count. A hard, cluster-wide limit needs a shared store (Redis behind
 * Spring Cloud Gateway's {@code RequestRateLimiter}); see infra/RUNBOOK.md.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthRateLimitFilter extends OncePerRequestFilter {

  static final int DEFAULT_MAX_PER_WINDOW = 10;
  static final long DEFAULT_WINDOW_MS = 60_000L;

  private static final long MILLIS_PER_SECOND = 1_000L;

  /**
   * The key is a caller-supplied {@code X-Forwarded-For}, so the map would grow without bound under
   * a spoofing attacker. Once it passes this many entries every expired window is dropped.
   */
  private static final int PURGE_THRESHOLD = 10_000;

  private final Map<String, Window> windows = new ConcurrentHashMap<>();
  private final Counter rejected;
  private final int maxPerWindow;
  private final long windowMs;
  private final String retryAfterSeconds;

  public AuthRateLimitFilter(
      MeterRegistry meters,
      @Value("${security.rate-limit.max-per-window:10}") int maxPerWindow,
      @Value("${security.rate-limit.window-ms:60000}") long windowMs) {
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

    purgeIfCrowded();
    Window window = windows.computeIfAbsent(clientKey(request), k -> new Window());
    if (window.tryAcquire(maxPerWindow, windowMs)) {
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

  /** Drops windows whose minute has elapsed - they carry no state worth keeping. */
  private void purgeIfCrowded() {
    if (windows.size() < PURGE_THRESHOLD) {
      return;
    }
    long now = System.currentTimeMillis();
    windows.values().removeIf(w -> w.isExpired(now, windowMs));
  }

  /** One caller's fixed-window counter. */
  private static final class Window {
    private long windowStart = System.currentTimeMillis();
    private int count;

    synchronized boolean tryAcquire(int max, long windowMs) {
      long now = System.currentTimeMillis();
      if (isExpired(now, windowMs)) {
        windowStart = now;
        count = 0;
      }
      if (count >= max) {
        return false;
      }
      count++;
      return true;
    }

    synchronized boolean isExpired(long now, long windowMs) {
      return now - windowStart >= windowMs;
    }
  }
}
