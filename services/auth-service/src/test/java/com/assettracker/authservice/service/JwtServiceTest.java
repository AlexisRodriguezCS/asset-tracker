package com.assettracker.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assettracker.authservice.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final long HOUR = 3_600_000L;
  private final JwtService jwt = new JwtService(HOUR, "asset-tracker-auth");

  @Test
  void tokenCarriesSubjectRoleAndClientIdsAndRoundTrips() {
    String token = jwt.generateToken("tech@acme.example", Role.TECH, List.of(1L, 2L, 3L), 7L);

    Claims claims = jwt.parse(token);
    assertThat(claims.getSubject()).isEqualTo("tech@acme.example");
    assertThat(claims.getIssuer()).isEqualTo("asset-tracker-auth");
    assertThat(claims.get("role", String.class)).isEqualTo("TECH");
    assertThat(claims.get("clientIds", List.class)).containsExactly(1, 2, 3);
  }

  @Test
  void aTokenSignedByADifferentKeyPairIsRejected() {
    JwtService other = new JwtService(HOUR, "asset-tracker-auth");
    String foreign = other.generateToken("x@y.z", Role.TECH, List.of(1L), null);
    assertThatThrownBy(() -> jwt.parse(foreign)).isInstanceOf(JwtException.class);
  }

  @Test
  void anExpiredTokenIsRejected() {
    JwtService instant = new JwtService(1L, "asset-tracker-auth");
    String token = instant.generateToken("x@y.z", Role.HR, List.of(1L), null);
    try {
      Thread.sleep(5);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    assertThatThrownBy(() -> jwt.parse(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void jwkSetPublishesAnRsaSigningKey() {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> keys = (List<Map<String, Object>>) jwt.jwkSet().get("keys");

    assertThat(keys).hasSize(1);
    Map<String, Object> jwk = keys.get(0);
    assertThat(jwk).containsEntry("kty", "RSA").containsEntry("alg", "RS256");
    assertThat((String) jwk.get("kid")).isNotBlank();
    assertThat((String) jwk.get("n")).isNotBlank();
    assertThat(jwk).containsEntry("e", "AQAB");
  }
}
