package com.assettracker.assignmentservice.config;

import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
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
 * a service account with rights of its own on every call, would let this service do things on its
 * own behalf that no user asked for, and would flatten the audit trail to "assignment-service did
 * it".
 *
 * <p>Relaying the user's token means asset-service applies the same tenant scoping and the same
 * role rules it would have applied to a direct call, and an expired session stops working
 * everywhere at once.
 *
 * <p>When there is no user - start-up seeding, and anything else the machine begins on its own - it
 * falls back to {@link ServiceAccountTokens}, which is an ordinary account with a role and a tenant
 * list. If that is not configured the call goes out unauthenticated and is refused, which is the
 * honest outcome: the alternative is a caller that is powerful precisely because nobody is
 * watching.
 */
public class BearerTokenPropagator implements ClientHttpRequestInterceptor {

  /**
   * Resolved per call, not at construction: {@link ServiceAccountTokens} is built from the very
   * builder this interceptor is attached to, and asking for it eagerly is a bean cycle.
   */
  private final ObjectProvider<ServiceAccountTokens> serviceAccount;

  public BearerTokenPropagator(ObjectProvider<ServiceAccountTokens> serviceAccount) {
    this.serviceAccount = serviceAccount;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

    if (SecurityContextHolder.getContext().getAuthentication()
        instanceof JwtAuthenticationToken authenticated) {
      request
          .getHeaders()
          .set(HttpHeaders.AUTHORIZATION, "Bearer " + authenticated.getToken().getTokenValue());
    } else if (!isSignIn(request)) {
      // Skipping sign-in matters twice over: fetching the service account's token would otherwise
      // re-enter this interceptor and recurse for ever, and a credentials endpoint has no business
      // receiving a bearer token in the first place.
      ServiceAccountTokens tokens = serviceAccount.getIfAvailable();
      if (tokens != null) {
        tokens
            .token()
            .ifPresent(
                token -> request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token));
      }
    }
    return execution.execute(request, body);
  }

  private static boolean isSignIn(HttpRequest request) {
    return request.getURI().getPath().endsWith("/auth/login");
  }
}
