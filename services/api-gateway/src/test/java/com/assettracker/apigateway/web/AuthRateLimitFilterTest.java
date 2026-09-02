package com.assettracker.apigateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {

  private final AuthRateLimitFilter filter = new AuthRateLimitFilter();

  @Test
  void allowsUpToTheLimitThenReturns429() throws Exception {
    for (int i = 0; i < AuthRateLimitFilter.MAX_PER_WINDOW; i++) {
      assertThat(post("1.2.3.4", "/api/auth/login").getStatus()).isEqualTo(200);
    }
    MockHttpServletResponse blocked = post("1.2.3.4", "/api/auth/login");
    assertThat(blocked.getStatus()).isEqualTo(429);
    assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
  }

  @Test
  void countsSeparatelyPerClientIp() throws Exception {
    for (int i = 0; i < AuthRateLimitFilter.MAX_PER_WINDOW; i++) {
      post("1.1.1.1", "/api/auth/login");
    }
    assertThat(post("2.2.2.2", "/api/auth/login").getStatus()).isEqualTo(200);
  }

  @Test
  void leavesNonAuthTrafficAlone() throws Exception {
    for (int i = 0; i < AuthRateLimitFilter.MAX_PER_WINDOW + 5; i++) {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/assets");
      request.setRemoteAddr("9.9.9.9");
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(request, response, new MockFilterChain());
      assertThat(response.getStatus()).isEqualTo(200);
    }
  }

  private MockHttpServletResponse post(String ip, String uri) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
    request.setRemoteAddr(ip);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }
}
