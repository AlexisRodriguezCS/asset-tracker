package com.assettracker.apigateway.config;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.util.StringUtils;

/**
 * Builds a per-issuer map of {@link AuthenticationManager}s: always the local auth-service issuer,
 * plus Microsoft Entra ID when {@code security.entra.issuer-uri} is configured.
 */
final class JwtAuthenticationManagers {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationManagers.class);
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private JwtAuthenticationManagers() {}

  static Map<String, AuthenticationManager> byIssuer(SecurityProperties props) {
    Map<String, AuthenticationManager> managers = new HashMap<>();

    SecurityProperties.Jwt.Local local = props.getJwt().getLocal();
    managers.put(local.getIssuer(), managerFor(localDecoder(local)));
    log.info("accepting local JWT issuer '{}'", local.getIssuer());

    String entraIssuer = props.getEntra().getIssuerUri();
    if (StringUtils.hasText(entraIssuer)) {
      managers.put(entraIssuer, managerFor(JwtDecoders.fromIssuerLocation(entraIssuer)));
      log.info("accepting Microsoft Entra issuer '{}'", entraIssuer);
    }
    return managers;
  }

  private static JwtDecoder localDecoder(SecurityProperties.Jwt.Local local) {
    byte[] keyBytes = local.getSecret().getBytes(StandardCharsets.UTF_8);
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(new SecretKeySpec(keyBytes, HMAC_ALGORITHM))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    OAuth2TokenValidator<Jwt> validator =
        new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(), new JwtIssuerValidator(local.getIssuer()));
    decoder.setJwtValidator(validator);
    return decoder;
  }

  private static AuthenticationManager managerFor(JwtDecoder decoder) {
    JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
    provider.setJwtAuthenticationConverter(rolesConverter());
    return new ProviderManager(provider);
  }

  private static JwtAuthenticationConverter rolesConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("roles");
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }
}
