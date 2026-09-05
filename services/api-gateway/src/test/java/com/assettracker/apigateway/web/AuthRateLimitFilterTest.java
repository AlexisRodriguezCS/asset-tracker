package com.assettracker.apigateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {

  private final AuthRateLimitFilter filter =
      new AuthRateLimitFilter(
          new SimpleMeterRegistry(),
          AuthRateLimitFilter.DEFAULT_MAX_PER_WINDOW,
          AuthRateLimitFilter.DEFAULT_WINDOW_MS);

  @Test
  void allowsUpToTheLimitThenReturns429() throws Exception {
    for (int i = 0; i < AuthRateLimitFilter.DEFAULT_MAX_PER_WINDOW; i++) {
      assertThat(post(filter, "1.2.3.4", "/api/auth/login").getStatus()).isEqualTo(200);
    }
    MockHttpServletResponse blocked = post(filter, "1.2.3.4", "/api/auth/login");
    assertThat(blocked.getStatus()).isEqualTo(429);
    assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
  }

  @Test
  void countsSeparatelyPerClientIp() throws Exception {
    for (int i = 0; i < AuthRateLimitFilter.DEFAULT_MAX_PER_WINDOW; i++) {
      post(filter, "1.1.1.1", "/api/auth/login");
    }
    assertThat(post(filter, "2.2.2.2", "/api/auth/login").getStatus()).isEqualTo(200);
  }

  @Test
  void leavesNonAuthTrafficAlone() throws Exception {
    for (int i = 0; i < AuthRateLimitFilter.DEFAULT_MAX_PER_WINDOW + 5; i++) {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/assets");
      request.setRemoteAddr("9.9.9.9");
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(request, response, new MockFilterChain());
      assertThat(response.getStatus()).isEqualTo(200);
    }
  }

  /**
   * A multi-replica deployment divides the budget by its replica count, so the limit has to come
   * from configuration rather than a constant.
   */
  @Test
  void honoursAConfiguredBudgetAndWindow() throws Exception {
    AuthRateLimitFilter tight = new AuthRateLimitFilter(new SimpleMeterRegistry(), 2, 30_000L);

    assertThat(post(tight, "5.5.5.5", "/api/auth/login").getStatus()).isEqualTo(200);
    assertThat(post(tight, "5.5.5.5", "/api/auth/login").getStatus()).isEqualTo(200);

    MockHttpServletResponse blocked = post(tight, "5.5.5.5", "/api/auth/login");
    assertThat(blocked.getStatus()).isEqualTo(429);
    assertThat(blocked.getHeader("Retry-After")).isEqualTo("30");
  }

  @Test
  void keysOnTheFirstForwardedForHopWhenBehindAProxy() throws Exception {
    for (int i = 0; i < AuthRateLimitFilter.DEFAULT_MAX_PER_WINDOW; i++) {
      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
      request.setRemoteAddr("10.0.0.1"); // the ingress, shared by every caller
      request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(request, response, new MockFilterChain());
      assertThat(response.getStatus()).isEqualTo(200);
    }

    // a different real client behind the same ingress is unaffected
    MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/auth/login");
    other.setRemoteAddr("10.0.0.1");
    other.addHeader("X-Forwarded-For", "198.51.100.4, 10.0.0.1");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(other, response, new MockFilterChain());
    assertThat(response.getStatus()).isEqualTo(200);
  }

  private MockHttpServletResponse post(AuthRateLimitFilter target, String ip, String uri)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
    request.setRemoteAddr(ip);
    MockHttpServletResponse response = new MockHttpServletResponse();
    target.doFilter(request, response, new MockFilterChain());
    return response;
  }
}
