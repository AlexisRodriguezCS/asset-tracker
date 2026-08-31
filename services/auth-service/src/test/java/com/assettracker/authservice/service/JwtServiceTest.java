package com.assettracker.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assettracker.authservice.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final String SECRET = "test-only-secret-at-least-32-bytes-long-000000";
  private final JwtService jwt = new JwtService(SECRET, 3_600_000L, "asset-tracker-auth");

  @Test
  void tokenCarriesSubjectRoleAndClientIdsAndRoundTrips() {
    String token = jwt.generateToken("tech@acme.example", Role.TECH, List.of(1L, 2L, 3L));

    Claims claims = jwt.parse(token);
    assertThat(claims.getSubject()).isEqualTo("tech@acme.example");
    assertThat(claims.getIssuer()).isEqualTo("asset-tracker-auth");
    assertThat(claims.get("role", String.class)).isEqualTo("TECH");
    assertThat(claims.get("clientIds", List.class)).containsExactly(1, 2, 3);
  }

  @Test
  void aTokenFromADifferentSecretIsRejected() {
    JwtService other =
        new JwtService(
            "another-secret-also-at-least-32-bytes-long-0", 3_600_000L, "asset-tracker-auth");
    String foreign = other.generateToken("x@y.z", Role.TECH, List.of(1L));
    assertThatThrownBy(() -> jwt.parse(foreign)).isInstanceOf(JwtException.class);
  }

  @Test
  void anExpiredTokenIsRejected() {
    JwtService instant = new JwtService(SECRET, 1L, "asset-tracker-auth");
    String token = instant.generateToken("x@y.z", Role.HR, List.of(1L));
    try {
      Thread.sleep(5);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    assertThatThrownBy(() -> jwt.parse(token)).isInstanceOf(JwtException.class);
  }
}
