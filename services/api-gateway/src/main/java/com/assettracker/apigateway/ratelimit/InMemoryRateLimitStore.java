package com.assettracker.apigateway.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed windows in this process's heap. The default, and correct for exactly one gateway.
 *
 * <p>With more than one it is a speed bump rather than a limit: each replica keeps its own count,
 * so the cluster allows roughly N times the budget, and a restart forgets everything. Configure a
 * shared store (see {@code RedisRateLimitStore}) wherever the gateway is scaled out.
 */
public class InMemoryRateLimitStore implements RateLimitStore {

  /**
   * The key is a caller-supplied {@code X-Forwarded-For}, so the map would grow without bound under
   * a spoofing attacker. Once it passes this many entries every expired window is dropped.
   */
  private static final int PURGE_THRESHOLD = 10_000;

  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  @Override
  public boolean tryAcquire(String key, int maxPerWindow, long windowMs) {
    purgeIfCrowded(windowMs);
    return windows.computeIfAbsent(key, k -> new Window()).tryAcquire(maxPerWindow, windowMs);
  }

  /** Drops windows whose minute has elapsed - they carry no state worth keeping. */
  private void purgeIfCrowded(long windowMs) {
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
