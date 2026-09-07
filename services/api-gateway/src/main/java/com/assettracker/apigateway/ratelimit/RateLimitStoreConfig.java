package com.assettracker.apigateway.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Picks the store from configuration, not from a profile: set {@code REDIS_HOST} and the limit
 * becomes cluster-wide, leave it blank and the gateway keeps its own counters.
 *
 * <p>Blank is the default so a clone still runs with nothing installed. The trade-off is stated out
 * loud in the log at startup rather than left for someone to discover from a metric.
 */
@Configuration
public class RateLimitStoreConfig {

  private static final Logger log = LoggerFactory.getLogger(RateLimitStoreConfig.class);

  @Bean
  @ConditionalOnProperty(name = "spring.data.redis.host")
  public RateLimitStore redisRateLimitStore(
      StringRedisTemplate redis,
      @Value("${security.rate-limit.key-prefix:asset-tracker:auth-rate:}") String prefix) {
    log.info("auth rate limit is cluster-wide, counted in Redis under '{}'", prefix);
    return new RedisRateLimitStore(redis, prefix);
  }

  @Bean
  @ConditionalOnMissingBean(RateLimitStore.class)
  public RateLimitStore inMemoryRateLimitStore() {
    log.info(
        "auth rate limit is per gateway instance - N replicas allow N times the budget and a"
            + " restart resets the counters. Set spring.data.redis.host for a cluster-wide limit.");
    return new InMemoryRateLimitStore();
  }
}
