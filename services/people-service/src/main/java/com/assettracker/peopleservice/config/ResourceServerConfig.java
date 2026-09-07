package com.assettracker.peopleservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * This service validates the caller's token itself.
 *
 * <p>It used to take the gateway's word for it. The identity headers - {@code X-User-Role}, {@code
 * X-Client-Ids} - were read straight off the request, and a missing role was treated as a trusted
 * internal call, so anything that could reach the port could read a tenant's data by sending no
 * headers at all. That is fine only while nothing else can reach the port, which is an assumption
 * about the network, not a control.
 *
 * <p>So the same RS256 token the gateway checks is checked again here, against auth-service's
 * published JWK set. The gateway still authenticates at the edge and still forwards the headers -
 * they remain useful for logging and for the audit trail - but authorization now comes from claims
 * this service verified. Defence in depth: the edge stops most of it, and a service is not
 * defenceless if something gets past.
 *
 * <p>Actuator stays open: probes have no token, and the endpoints exposed are health and metrics.
 *
 * <p>Servlet contexts only. The Postgres migration test boots this application with {@code
 * web-application-type=none} to check the schema, and there is no HttpSecurity to configure in a
 * context with no web layer.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ResourceServerConfig {

  @Bean
  public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(server -> server.jwt(Customizer.withDefaults()))
        // 401 rather than a redirect to a login page this service does not have
        .exceptionHandling(
            handling ->
                handling.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .build();
  }

  /**
   * Built here rather than from {@code spring.security.oauth2.*} so the issuer check is explicit
   * and RS256 is pinned. The issuer is a plain name, not a URL, so the discovery the
   * property-driven path would attempt has nothing to fetch.
   */
  @Bean
  public JwtDecoder jwtDecoder(
      @Value("${security.jwt.jwk-set-uri}") String jwkSetUri,
      @Value("${security.jwt.issuer}") String issuer) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(jwkSetUri).jwsAlgorithm(SignatureAlgorithm.RS256).build();
    OAuth2TokenValidator<Jwt> validator =
        new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(), new JwtIssuerValidator(issuer));
    decoder.setJwtValidator(validator);
    return decoder;
  }
}
