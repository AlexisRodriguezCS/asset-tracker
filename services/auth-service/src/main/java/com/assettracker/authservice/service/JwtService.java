package com.assettracker.authservice.service;

import com.assettracker.authservice.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies HS256 JSON Web Tokens.
 *
 * <p>The gateway validates these tokens with the same shared secret and expected issuer. Upgrading
 * to RS256 + a JWKS endpoint is a planned follow-up (see infra/RUNBOOK.md).
 */
@Service
public class JwtService {

  /** Pinned so the algorithm never varies with secret length. */
  private static final MacAlgorithm ALGORITHM = Jwts.SIG.HS256;

  private final SecretKey signingKey;
  private final long expirationMs;
  private final String issuer;

  /**
   * Builds the service from configured signing material.
   *
   * @param secret shared HS256 secret (must be at least 32 bytes)
   * @param expirationMs token lifetime in milliseconds
   * @param issuer value placed in, and required on, the {@code iss} claim
   */
  public JwtService(
      @Value("${security.jwt.secret}") String secret,
      @Value("${security.jwt.expiration-ms}") long expirationMs,
      @Value("${security.jwt.issuer}") String issuer) {
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirationMs = expirationMs;
    this.issuer = issuer;
  }

  /**
   * Creates a signed token. Beyond the standard claims it carries {@code role} (a single {@link
   * Role}) and {@code clientIds} (the tenant ids this user may act on) - the gateway forwards both
   * downstream so services can scope and authorize without re-reading the database.
   */
  public String generateToken(String subject, Role role, List<Long> clientIds) {
    Date now = new Date();
    return Jwts.builder()
        .issuer(issuer)
        .subject(subject)
        .claim("role", role.name())
        .claim("clientIds", clientIds)
        .issuedAt(now)
        .expiration(new Date(now.getTime() + expirationMs))
        .signWith(signingKey, ALGORITHM)
        .compact();
  }

  /**
   * Verifies the signature, issuer and expiry, returning the token claims.
   *
   * @throws JwtException if the token is invalid or expired
   */
  public Claims parse(String token) {
    return Jwts.parser()
        .verifyWith(signingKey)
        .requireIssuer(issuer)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public long getExpirationMs() {
    return expirationMs;
  }
}
