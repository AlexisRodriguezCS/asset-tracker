package com.assettracker.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Cryptography beans. */
@Configuration
public class CryptoConfig {

  /** BCrypt work factor (cost). Higher is slower and more resistant to brute force. */
  private static final int BCRYPT_STRENGTH = 10;

  /** Password hashing for registration and login verification. */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
  }
}
