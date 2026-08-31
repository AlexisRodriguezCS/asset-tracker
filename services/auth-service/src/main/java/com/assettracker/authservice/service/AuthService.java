package com.assettracker.authservice.service;

import com.assettracker.authservice.dto.LoginRequest;
import com.assettracker.authservice.dto.RegisterRequest;
import com.assettracker.authservice.dto.TokenResponse;
import com.assettracker.authservice.entity.Role;
import com.assettracker.authservice.entity.User;
import com.assettracker.authservice.repository.UserRepository;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration and login. */
@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthService(
      UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  /**
   * Registers a new user with a BCrypt-hashed password.
   *
   * @throws IllegalArgumentException if the email is already registered
   */
  @Transactional
  public void register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new IllegalArgumentException("Email already registered");
    }
    Role role = request.role() == null ? Role.TECH : request.role();
    Set<Long> clientIds = request.clientIds() == null ? Set.of() : Set.copyOf(request.clientIds());
    userRepository.save(
        new User(request.email(), passwordEncoder.encode(request.password()), role, clientIds));
  }

  /**
   * Verifies credentials and returns a signed token carrying the user's role and client ids.
   *
   * @throws BadCredentialsException if the email is unknown or the password does not match
   */
  @Transactional(readOnly = true)
  public TokenResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new BadCredentialsException("Invalid credentials");
    }
    String token =
        jwtService.generateToken(user.getEmail(), user.getRole(), List.copyOf(user.getClientIds()));
    return TokenResponse.bearer(token, jwtService.getExpirationMs());
  }
}
