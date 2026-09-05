package com.assettracker.authservice.bootstrap;

import com.assettracker.authservice.entity.Role;
import com.assettracker.authservice.entity.User;
import com.assettracker.authservice.repository.UserRepository;
import java.util.Set;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds one login per role on an empty database (dev only), so every view in the console can be
 * demonstrated without hand-building accounts. All share the password {@code Passw0rd!}.
 *
 * <p>{@code dana.reyes@acme.example} is deliberately the email of the first person PeopleSeeder
 * inserts, and is pinned to that person's id - it is the account that shows "my assigned assets". A
 * real deployment provisions this link from the directory at user-creation time.
 */
@Component
@Profile("!prod")
public class UserSeeder implements CommandLineRunner {

  /** Matches PeopleSeeder's first row (Dana Reyes, Acme) on a fresh database. */
  private static final long DEMO_PERSON_ID = 1L;

  private static final Set<Long> ALL_CLIENTS = Set.of(1L, 2L, 3L);
  private static final Set<Long> ACME = Set.of(1L);

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
    repository.save(new User("admin@platform.example", hash, Role.ADMIN, ALL_CLIENTS));
    repository.save(new User("tech@acme.example", hash, Role.TECH, ALL_CLIENTS));
    repository.save(new User("poc@acme.example", hash, Role.POC, ACME));
    repository.save(new User("hr@acme.example", hash, Role.HR, ACME));
    repository.save(new User("dana.reyes@acme.example", hash, Role.USER, ACME, DEMO_PERSON_ID));
  }
}
