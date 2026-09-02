package com.assettracker.authservice.controller;

import com.assettracker.authservice.dto.LoginRequest;
import com.assettracker.authservice.dto.MicrosoftTokenRequest;
import com.assettracker.authservice.dto.RegisterRequest;
import com.assettracker.authservice.dto.TokenResponse;
import com.assettracker.authservice.service.AuthService;
import com.assettracker.authservice.service.JwtService;
import com.assettracker.authservice.service.MicrosoftAuthService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Authentication endpoints: register, login, and token inspection. */
@RestController
@RequestMapping("/auth")
public class AuthController {

  private static final String BEARER_PREFIX = "Bearer ";

  private final AuthService authService;
  private final JwtService jwtService;
  private final MicrosoftAuthService microsoftAuthService;

  public AuthController(
      AuthService authService, JwtService jwtService, MicrosoftAuthService microsoftAuthService) {
    this.authService = authService;
    this.jwtService = jwtService;
    this.microsoftAuthService = microsoftAuthService;
  }

  /** Registers a new account. */
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public void register(@Valid @RequestBody RegisterRequest request) {
    authService.register(request);
  }

  /** Exchanges credentials for a signed token. */
  @PostMapping("/login")
  public TokenResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  /**
   * Exchanges a Microsoft Entra ID id-token (obtained by the console's OIDC callback) for a local
   * session token. Answers 501 when Microsoft sign-in is not configured on this deployment.
   */
  @PostMapping("/microsoft")
  public TokenResponse microsoft(@Valid @RequestBody MicrosoftTokenRequest request) {
    return microsoftAuthService.exchange(request.idToken());
  }

  /** Validates a bearer token and echoes its subject, role and client ids. */
  @GetMapping("/validate")
  public ResponseEntity<Map<String, Object>> validate(
      @RequestHeader("Authorization") String authorization) {
    Claims claims = jwtService.parse(stripBearer(authorization));
    return ResponseEntity.ok(
        Map.of(
            "subject", claims.getSubject(),
            "role", claims.getOrDefault("role", "TECH"),
            "clientIds", claims.getOrDefault("clientIds", "[]")));
  }

  private static String stripBearer(String header) {
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length());
    }
    return header;
  }
}
