package com.assettracker.apigateway.ratelimit;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * One counter per caller, shared by every gateway replica.
 *
 * <p>The increment and the expiry are one Lua script rather than two calls, and that is the whole
 * point of the class. Redis runs a script atomically, so two replicas incrementing the same key
 * cannot interleave; done as INCR-then-EXPIRE, a process dying in between would leave a key with no
 * TTL, which locks that caller out until someone notices.
 *
 * <p>Fails open. If Redis is unreachable the brake stops working and the login endpoint stays up -
 * the opposite choice turns a cache outage into a total authentication outage, which is a far worse
 * failure than a window of unthrottled attempts. Every failure is logged so it cannot pass quietly.
 */
public class RedisRateLimitStore implements RateLimitStore {

  private static final Logger log = LoggerFactory.getLogger(RedisRateLimitStore.class);

  private static final RedisScript<Long> INCREMENT_AND_EXPIRE =
      new DefaultRedisScript<>(
          """
          local hits = redis.call('INCR', KEYS[1])
          if hits == 1 then
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
          end
          return hits
          """,
          Long.class);

  private final StringRedisTemplate redis;
  private final String prefix;

  public RedisRateLimitStore(StringRedisTemplate redis, String prefix) {
    this.redis = redis;
    this.prefix = prefix;
  }

  @Override
  public boolean tryAcquire(String key, int maxPerWindow, long windowMs) {
    try {
      Long hits =
          redis.execute(INCREMENT_AND_EXPIRE, List.of(prefix + key), String.valueOf(windowMs));
      return hits == null || hits <= maxPerWindow;
    } catch (DataAccessException unreachable) {
      log.warn("rate limit store unavailable, allowing the request: {}", unreachable.getMessage());
      return true;
    }
  }
}
