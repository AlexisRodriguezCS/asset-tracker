package com.assettracker.locationservice.config;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Carries the caller's token on to the next service.
 *
 * <p>Now that every service validates tokens rather than trusting headers, an internal call has to
 * present one - and the right one to present is the token of the person who asked. The alternative,
 * a service account with rights of its own, would let this service do things on its own behalf that
 * no user asked for, and would flatten the audit trail to "assignment-service did it".
 *
 * <p>Relaying the user's token instead means asset-service applies the same tenant scoping and the
 * same role rules it would have applied to a direct call, and an expired session stops working
 * everywhere at once.
 */
public class BearerTokenPropagator implements ClientHttpRequestInterceptor {

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    if (SecurityContextHolder.getContext().getAuthentication()
        instanceof JwtAuthenticationToken authenticated) {
      request
          .getHeaders()
          .set(HttpHeaders.AUTHORIZATION, "Bearer " + authenticated.getToken().getTokenValue());
    }
    return execution.execute(request, body);
  }
}
