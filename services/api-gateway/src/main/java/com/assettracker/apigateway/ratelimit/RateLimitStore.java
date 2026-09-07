package com.assettracker.apigateway.ratelimit;

/**
 * Where the attempt counters live.
 *
 * <p>The interface exists because the answer differs by deployment, not by preference: one gateway
 * on a laptop needs nothing shared, and several behind a load balancer need a store they agree on -
 * otherwise N replicas allow N times the budget and a rolling restart resets every counter.
 */
public interface RateLimitStore {

  /**
   * Counts one attempt against {@code key} and says whether it is within budget.
   *
   * @return true to let the request through
   */
  boolean tryAcquire(String key, int maxPerWindow, long windowMs);
}
