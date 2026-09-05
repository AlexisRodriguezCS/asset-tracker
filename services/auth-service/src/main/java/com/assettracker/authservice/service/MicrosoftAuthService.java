package com.assettracker.authservice.service;

import com.assettracker.authservice.config.EntraProperties;
import com.assettracker.authservice.dto.TokenResponse;
import com.assettracker.authservice.entity.Role;
import com.assettracker.authservice.entity.User;
import com.assettracker.authservice.repository.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exchanges a Microsoft Entra ID id-token for a local session token. The id-token's signature,
 * issuer and expiry are checked against Entra's published keys; a first-seen user is provisioned
 * with the default role and client ids. The rest of the platform keeps seeing an ordinary
 * auth-service RS256 token, so nothing downstream changes.
 */
@Service
public class MicrosoftAuthService {

  private static final Logger log = LoggerFactory.getLogger(MicrosoftAuthService.class);

  private final EntraProperties props;
  private final UserRepository users;
  private final JwtService jwtService;
  private final PasswordEncoder passwordEncoder;

  /** Built lazily on first use so an unreachable Entra endpoint never blocks start-up. */
  private volatile JwtDecoder decoder;

  public MicrosoftAuthService(
      EntraProperties props,
      UserRepository users,
      JwtService jwtService,
      PasswordEncoder passwordEncoder) {
    this.props = props;
    this.users = users;
    this.jwtService = jwtService;
    this.passwordEncoder = passwordEncoder;
  }

  public boolean isConfigured() {
    return props.isConfigured();
  }

  @Transactional
  public TokenResponse exchange(String idToken) {
    if (!isConfigured()) {
      throw new MicrosoftAuthNotConfiguredException();
    }

    Jwt token;
    try {
      token = decoder().decode(idToken);
    } catch (JwtException e) {
      throw new BadCredentialsException("Microsoft token rejected: " + e.getMessage());
    }
    if (!token.getAudience().contains(props.getClientId())) {
      throw new BadCredentialsException("Microsoft token audience mismatch");
    }

    String email =
        firstNonBlank(
            token.getClaimAsString("preferred_username"),
            token.getClaimAsString("email"),
            token.getClaimAsString("upn"));
    if (email == null) {
      throw new BadCredentialsException("Microsoft token carries no username claim");
    }
    String normalised = email.toLowerCase();

    User user =
        users
            .findByEmail(normalised)
            .orElseGet(
                () -> {
                  log.info("provisioning first-seen Microsoft user {}", normalised);
                  return users.save(
                      new User(
                          normalised,
                          // no local password path for a federated account
                          passwordEncoder.encode(UUID.randomUUID().toString()),
                          Role.TECH,
                          Set.copyOf(props.getDefaultClientIds())));
                });

    String local =
        jwtService.generateToken(
            user.getEmail(), user.getRole(), List.copyOf(user.getClientIds()), user.getPersonId());
    return TokenResponse.bearer(local, jwtService.getExpirationMs());
  }

  private JwtDecoder decoder() {
    JwtDecoder d = decoder;
    if (d == null) {
      synchronized (this) {
        d = decoder;
        if (d == null) {
          d = JwtDecoders.fromIssuerLocation(props.getIssuerUri());
          decoder = d;
        }
      }
    }
    return d;
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return null;
  }
}
