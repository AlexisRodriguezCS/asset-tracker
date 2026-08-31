package com.assettracker.authservice.bootstrap;

import com.assettracker.authservice.entity.Role;
import com.assettracker.authservice.entity.User;
import com.assettracker.authservice.repository.UserRepository;
import java.util.Set;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Seeds a demo tech (all clients) and an HR user (Acme only) on an empty database (dev only). */
@Component
@Profile("!prod")
public class UserSeeder implements CommandLineRunner {

  private final UserRepository repository;
  private final PasswordEncoder encoder;

  public UserSeeder(UserRepository repository, PasswordEncoder encoder) {
    this.repository = repository;
    this.encoder = encoder;
  }

  @Override
  public void run(String... args) {
    if (repository.count() > 0) {
      return;
    }
    String hash = encoder.encode("Passw0rd!");
    repository.save(new User("tech@acme.example", hash, Role.TECH, Set.of(1L, 2L, 3L)));
    repository.save(new User("hr@acme.example", hash, Role.HR, Set.of(1L)));
    repository.save(new User("admin@platform.example", hash, Role.ADMIN, Set.of(1L, 2L, 3L)));
  }
}
