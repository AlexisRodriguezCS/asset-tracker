package com.assettracker.authservice.service;

import com.assettracker.authservice.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies RS256 JSON Web Tokens.
 *
 * <p>An RSA-2048 key pair is generated at startup. Tokens are signed with the private key; the
 * public key is published as a one-entry JWK Set (see {@code JwksController}) that the gateway
 * fetches to validate tokens - no shared secret crosses the mesh. Tokens do not survive an
 * auth-service restart, which is fine for this deployment; a persisted / KMS-backed key is the
 * production step (see infra/RUNBOOK.md).
 */
@Service
public class JwtService {

  private static final int KEY_SIZE = 2048;
  private static final int KID_LENGTH = 16;

  private final KeyPair keyPair;
  private final String keyId;
  private final long expirationMs;
  private final String issuer;

  public JwtService(
      @Value("${security.jwt.expiration-ms}") long expirationMs,
      @Value("${security.jwt.issuer}") String issuer) {
    this.keyPair = generateRsaKeyPair();
    this.keyId = keyId(keyPair.getPublic());
    this.expirationMs = expirationMs;
    this.issuer = issuer;
  }

  /**
   * Creates a signed token carrying {@code role} (a single {@link Role}) and {@code clientIds} (the
   * tenant ids this user may act on) and {@code personId} (the employee record this login is, if
   * any) - the gateway forwards all three downstream so services can scope and authorize without
   * re-reading the database.
   */
  public String generateToken(String subject, Role role, List<Long> clientIds, Long personId) {
    Date now = new Date();
    return Jwts.builder()
        .header()
        .keyId(keyId)
        .and()
        .issuer(issuer)
        .subject(subject)
        .claim("role", role.name())
        .claim("clientIds", clientIds)
        .claim("personId", personId)
        .issuedAt(now)
        .expiration(new Date(now.getTime() + expirationMs))
        .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
        .compact();
  }

  /**
   * Verifies the signature, issuer and expiry, returning the token claims.
   *
   * @throws JwtException if the token is invalid or expired
   */
  public Claims parse(String token) {
    return Jwts.parser()
        .verifyWith(keyPair.getPublic())
        .requireIssuer(issuer)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  /** The public key as a single-entry JWK Set for the gateway to fetch and cache. */
  public Map<String, Object> jwkSet() {
    RSAPublicKey pub = (RSAPublicKey) keyPair.getPublic();
    Map<String, Object> jwk = new LinkedHashMap<>();
    jwk.put("kty", "RSA");
    jwk.put("use", "sig");
    jwk.put("alg", "RS256");
    jwk.put("kid", keyId);
    jwk.put("n", base64Url(pub.getModulus()));
    jwk.put("e", base64Url(pub.getPublicExponent()));
    return Map.of("keys", List.of(jwk));
  }

  public long getExpirationMs() {
    return expirationMs;
  }

  private static String base64Url(BigInteger value) {
    byte[] bytes = value.toByteArray();
    if (bytes.length > 1 && bytes[0] == 0) {
      bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static KeyPair generateRsaKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(KEY_SIZE);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("RSA key generation unavailable", e);
    }
  }

  private static String keyId(PublicKey key) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
      return HexFormat.of().formatHex(hash).substring(0, KID_LENGTH);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
