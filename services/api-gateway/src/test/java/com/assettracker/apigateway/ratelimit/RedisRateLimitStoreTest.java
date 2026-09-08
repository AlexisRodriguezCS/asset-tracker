package com.assettracker.apigateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisRateLimitStoreTest {

  @Mock StringRedisTemplate redis;

  @Test
  @SuppressWarnings("unchecked")
  void allowsUntilTheSharedCounterPassesTheBudget() {
    RedisRateLimitStore store = new RedisRateLimitStore(redis, "p:", new SimpleMeterRegistry());
    when(redis.execute(any(RedisScript.class), any(), anyString())).thenReturn(10L, 11L);

    // the tenth attempt across the whole cluster is still within a budget of ten
    assertThat(store.tryAcquire("1.2.3.4", 10, 60_000L)).isTrue();
    assertThat(store.tryAcquire("1.2.3.4", 10, 60_000L)).isFalse();
  }

  /**
   * Deliberately fails open. A Redis outage that also took down sign-in would turn a cache problem
   * into a total authentication outage - much worse than a window of unthrottled attempts.
   */
  @Test
  @SuppressWarnings("unchecked")
  void anUnreachableStoreLetsTheRequestThrough() {
    RedisRateLimitStore store = new RedisRateLimitStore(redis, "p:", new SimpleMeterRegistry());
    when(redis.execute(any(RedisScript.class), any(), anyString()))
        .thenThrow(new RedisConnectionFailureException("down"));

    assertThat(store.tryAcquire("1.2.3.4", 10, 60_000L)).isTrue();
  }
}
