package com.assettracker.authservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assettracker.authservice.entity.Role;
import com.assettracker.authservice.service.JwtService;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The behaviour a configured key exists for: two instances that agree about signatures. */
class SigningKeyProviderTest {

  private static final long HOUR = 3_600_000L;

  @Test
  void twoInstancesGivenTheSameKeyAcceptEachOthersTokens() {
    String pem = pem(generate());

    JwtService one = new JwtService(SigningKeyProvider.fromPem(pem), HOUR, "asset-tracker-auth");
    JwtService two = new JwtService(SigningKeyProvider.fromPem(pem), HOUR, "asset-tracker-auth");

    // the whole point: a token minted by one replica validates on the other, which is what makes
    // running more than one of them - or restarting one - survivable
    String token = one.generateToken("tech@acme.example", Role.TECH, List.of(1L), 7L);
    assertThat(two.parse(token).getSubject()).isEqualTo("tech@acme.example");

    // and the gateway caches JWKS by kid, so the two must advertise the same one
    assertThat(kid(two)).isEqualTo(kid(one));
  }

  @Test
  void theStandardPemHeaderAndLineBreaksAreAccepted() {
    KeyPair generated = generate();
    String wrapped =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(generated.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";

    KeyPair loaded = SigningKeyProvider.fromPem(wrapped);

    assertThat(loaded.getPrivate()).isEqualTo(generated.getPrivate());
    // the public half is derived from the private key's CRT parameters, not configured separately
    assertThat(loaded.getPublic()).isEqualTo(generated.getPublic());
  }

  @Test
  void anUnreadableKeyFailsFastAndSaysNothingAboutTheKeyItself() {
    assertThatThrownBy(() -> SigningKeyProvider.fromPem("-----BEGIN PRIVATE KEY-----\nnot-a-key"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PKCS#8")
        .hasMessageNotContaining("not-a-key");
  }

  private static String kid(JwtService service) {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> keys = (List<Map<String, Object>>) service.jwkSet().get("keys");
    return (String) keys.get(0).get("kid");
  }

  private static String pem(KeyPair pair) {
    return "-----BEGIN PRIVATE KEY-----\n"
        + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())
        + "\n-----END PRIVATE KEY-----";
  }

  private static KeyPair generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
