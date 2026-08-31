package com.assettracker.apigateway.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The single public entry point. Open to anyone: {@code /api/auth/**}, actuator, and read-only
 * ({@code GET}) browsing of assets / people / locations / assignments / clients so a viewer can
 * look around without an account. Any write - check-out, return, offboard, create, edit - needs a
 * valid bearer token from a trusted issuer. Per-tenant authorization (a token may only touch its
 * own {@code clientIds}) is enforced downstream from the headers this gateway forwards.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

  private static final String[] READ_ONLY_PUBLIC = {
    "/api/assets/**",
    "/api/people/**",
    "/api/locations/**",
    "/api/assignments/**",
    "/api/clients/**",
    "/api/notifications/**"
  };

  private final SecurityProperties properties;

  public SecurityConfig(SecurityProperties properties) {
    this.properties = properties;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/auth/**", "/actuator/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, READ_ONLY_PUBLIC)
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 -> oauth2.authenticationManagerResolver(authenticationManagerResolver()));
    return http.build();
  }

  @Bean
  public AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver() {
    return new JwtIssuerAuthenticationManagerResolver(
        JwtAuthenticationManagers.byIssuer(properties)::get);
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(properties.getCors().getAllowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
