package com.assettracker.authservice.controller;

import com.assettracker.authservice.service.JwtService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes the token-signing public key as a JWK Set. The gateway points its resource-server
 * decoder at this, so validation needs no shared secret.
 */
@RestController
public class JwksController {

  private final JwtService jwtService;

  public JwksController(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @GetMapping({"/.well-known/jwks.json", "/oauth2/jwks"})
  public Map<String, Object> jwks() {
    return jwtService.jwkSet();
  }
}
