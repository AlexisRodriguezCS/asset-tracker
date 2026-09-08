package com.assettracker.assignmentservice.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * A token for the work no person asked for.
 *
 * <p>Almost every call this service makes is on behalf of somebody, and those relay that person's
 * token. Start-up seeding is the exception: it runs before any request exists, and once
 * asset-service began checking tokens instead of trusting headers, its read came back 401 - so a
 * fresh stack seeded no custody history at all and the demo employee owned nothing. The local stack
 * hid it, because its database was already seeded from before the change; CI, which starts empty
 * every time, failed on the first run.
 *
 * <p>So machine-initiated calls sign in as a configured account and use its token. Nothing here is
 * privileged by being internal: the account has a role and a tenant list like any other, and
 * asset-service applies the same rules to it as to a person.
 *
 * <p>Blank credentials mean no token, and callers proceed without one - which is what a deployment
 * that does no seeding should be configured to do. It is deliberately not defaulted to a demo
 * login: a credential that ships in the image is worse than a feature that stays off until it is
 * asked for.
 */
@Component
public class ServiceAccountTokens {

  private static final Logger log = LoggerFactory.getLogger(ServiceAccountTokens.class);

  /**
   * Re-fetched this long before the token expires, so a long seed never runs off the end of one.
   */
  private static final Duration REFRESH_MARGIN = Duration.ofMinutes(1);

  private final RestClient authService;
  private final String email;
  private final String password;

  private volatile String token;
  private volatile Instant renewAt = Instant.EPOCH;

  public ServiceAccountTokens(
      RestClient.Builder loadBalancedRestClientBuilder,
      @Value("${downstream.auth-service}") String baseUrl,
      @Value("${security.service-account.email:}") String email,
      @Value("${security.service-account.password:}") String password) {
    this.authService = loadBalancedRestClientBuilder.baseUrl(baseUrl).build();
    this.email = email;
    this.password = password;
  }

  public boolean isConfigured() {
    return StringUtils.hasText(email) && StringUtils.hasText(password);
  }

  /** The current token, fetching one if needed. Empty when unconfigured or auth-service refuses. */
  public synchronized Optional<String> token() {
    if (!isConfigured()) {
      return Optional.empty();
    }
    if (token != null && Instant.now().isBefore(renewAt)) {
      return Optional.of(token);
    }
    return Optional.ofNullable(signIn());
  }

  private String signIn() {
    try {
      TokenResponse response =
          authService
              .post()
              .uri("/auth/login")
              .body(Map.of("email", email, "password", password))
              .retrieve()
              .body(TokenResponse.class);
      if (response == null || !StringUtils.hasText(response.token())) {
        return null;
      }
      token = response.token();
      renewAt = Instant.now().plusMillis(response.expiresInMs()).minus(REFRESH_MARGIN);
      log.info("signed in as the service account {}", email);
      return token;
    } catch (RuntimeException refused) {
      // never the credentials, and never at error: start-up ordering means the first few of these
      // are ordinary, and the caller reports the failure that actually matters
      log.warn("service account sign-in failed: {}", refused.getMessage());
      token = null;
      return null;
    }
  }

  /** Only the two fields this needs; auth-service sends more. */
  record TokenResponse(String token, long expiresInMs) {}
}
