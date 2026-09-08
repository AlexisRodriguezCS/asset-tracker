package com.assettracker.authservice.bootstrap;

import com.assettracker.authservice.entity.Role;
import com.assettracker.authservice.entity.User;
import com.assettracker.authservice.repository.UserRepository;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The first account in a real deployment.
 *
 * <p>Every seeder in this project is {@code @Profile("!prod")}, which is right - nobody wants
 * dana.reyes@acme.example in production - but it left the prod profile with no way in at all. The
 * stack came up healthy on Postgres, and every sign-in returned 401 because the users table was
 * empty. That is not a bug you find by reading; it was found by running the stack on Postgres for
 * the first time, which until now nothing had done.
 *
 * <p>So: when the table is empty and credentials are configured, one ADMIN is created. Both halves
 * matter. Configured but users already present does nothing, so a restart cannot resurrect a
 * deleted account or reset a changed password. Empty but unconfigured says loudly in the log how to
 * fix it, because the alternative is an operator staring at a 401 with nothing to go on.
 *
 * <p>The password is read once, hashed, and never logged. Rotate it after the first sign-in like
 * any bootstrap credential; the intended shape for later is a client-credentials or workload
 * identity flow, which is tracked with the other secret-handling work.
 */
@Component
public class BootstrapAdmin implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

  private final UserRepository repository;
  private final PasswordEncoder encoder;
  private final String email;
  private final String password;
  private final String clientIds;

  public BootstrapAdmin(
      UserRepository repository,
      PasswordEncoder encoder,
      @Value("${security.bootstrap.email:}") String email,
      @Value("${security.bootstrap.password:}") String password,
      @Value("${security.bootstrap.client-ids:1}") String clientIds) {
    this.repository = repository;
    this.encoder = encoder;
    this.email = email;
    this.password = password;
    this.clientIds = clientIds;
  }

  @Override
  public void run(String... args) {
    if (repository.count() > 0) {
      return;
    }
    if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
      log.warn(
          "no accounts exist and no bootstrap admin is configured - every sign-in will be refused."
              + " Set SECURITY_BOOTSTRAP_EMAIL and SECURITY_BOOTSTRAP_PASSWORD, restart, then"
              + " change that password.");
      return;
    }

    repository.save(new User(email, encoder.encode(password), Role.ADMIN, tenants()));
    log.info("created the bootstrap admin {} - change its password after signing in", email);
  }

  /** The tenants this first admin may act on. One is the common case: the client they create. */
  private Set<Long> tenants() {
    return Arrays.stream(clientIds.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(Long::valueOf)
        .collect(Collectors.toUnmodifiableSet());
  }
}
