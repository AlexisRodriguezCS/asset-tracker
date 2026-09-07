package com.assettracker.authservice.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

/**
 * Where the RS256 signing key comes from.
 *
 * <p>It used to be generated in memory at startup, which had two consequences that were easy to
 * miss and expensive to hit. A restart minted a new key, so every token in flight failed its
 * signature check and every signed-in person - including anyone who came in through Microsoft 365,
 * since that exchange also ends in a locally signed token - was bounced to the sign-in page. And
 * two replicas held two different keys, so a token issued by one failed against the other's JWK set
 * roughly half the time; the deployment pinned auth-service to a single replica to hide it. That
 * pin meant the one service every authenticated request depends on had no redundancy and no rolling
 * deploy.
 *
 * <p>Resolution order:
 *
 * <ol>
 *   <li>{@code security.jwt.private-key} - a PKCS#8 PEM, for a secret injected as an environment
 *       variable (Key Vault, Secrets Manager, a Kubernetes secret).
 *   <li>{@code security.jwt.private-key-location} - any Spring resource, for a secret mounted as a
 *       file, which is the shape most secret stores prefer.
 *   <li>Neither: generate one, and say plainly in the log what that costs. Convenient for a laptop,
 *       wrong anywhere with more than one instance or an uptime expectation.
 * </ol>
 *
 * <p>Only the private key is configured. An RSA private key in PKCS#8 form is a CRT key, so it
 * already carries the modulus and public exponent - deriving the public half here means there is
 * one secret to rotate rather than two things to keep in step.
 */
@Configuration
public class SigningKeyProvider {

  private static final Logger log = LoggerFactory.getLogger(SigningKeyProvider.class);
  private static final int KEY_SIZE = 2048;

  private final ResourceLoader resources;

  public SigningKeyProvider(ResourceLoader resources) {
    this.resources = resources;
  }

  @Bean
  public KeyPair jwtSigningKeyPair(
      @Value("${security.jwt.private-key:}") String pem,
      @Value("${security.jwt.private-key-location:}") String location) {

    if (StringUtils.hasText(pem)) {
      log.info("signing key loaded from security.jwt.private-key");
      return fromPem(pem);
    }
    if (StringUtils.hasText(location)) {
      log.info("signing key loaded from {}", location);
      return fromPem(read(resources.getResource(location)));
    }

    log.warn(
        "no signing key configured - generating one. Tokens will not survive a restart and a second"
            + " replica would reject this one's tokens. Set security.jwt.private-key or"
            + " security.jwt.private-key-location outside development.");
    return generate();
  }

  /** Parses a PKCS#8 PEM and derives the public half from the private key's CRT parameters. */
  static KeyPair fromPem(String pem) {
    String base64 =
        pem.replaceAll("-----BEGIN (RSA )?PRIVATE KEY-----", "")
            .replaceAll("-----END (RSA )?PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    if (base64.isEmpty()) {
      throw new IllegalStateException("signing key is present but contains no PEM body");
    }
    try {
      byte[] der = Base64.getDecoder().decode(base64);
      KeyFactory rsa = KeyFactory.getInstance("RSA");
      PrivateKey privateKey = rsa.generatePrivate(new PKCS8EncodedKeySpec(der));
      if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
        throw new IllegalStateException(
            "signing key is not an RSA CRT key, so its public half cannot be derived");
      }
      return new KeyPair(
          rsa.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent())),
          privateKey);
    } catch (IllegalArgumentException | InvalidKeySpecException | NoSuchAlgorithmException e) {
      // deliberately terse: never log or wrap the key material itself
      throw new IllegalStateException("signing key could not be read as a PKCS#8 RSA PEM", e);
    }
  }

  private static String read(Resource resource) {
    try (InputStream in = resource.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("signing key file could not be read: " + resource, e);
    }
  }

  private static KeyPair generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(KEY_SIZE);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("RSA key generation unavailable", e);
    }
  }
}
