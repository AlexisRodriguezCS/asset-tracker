package com.assettracker.authservice.service;

import com.assettracker.authservice.entity.User;
import com.assettracker.authservice.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads user-specific data for authentication. */
@Service
public class CustomUserDetailService implements UserDetailsService {

  private final UserRepository userRepository;

  public CustomUserDetailService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  /** Loads a user by their email (used as the username identifier). */
  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

    return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
        .password(user.getPassword())
        .roles(user.getRole().name())
        .build();
  }
}
