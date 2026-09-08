package com.assettracker.authservice.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assettracker.authservice.entity.Role;
import com.assettracker.authservice.entity.User;
import com.assettracker.authservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

/** The account that makes a fresh production deployment reachable at all. */
@ExtendWith(MockitoExtension.class)
class BootstrapAdminTest {

  @Mock UserRepository repository;

  @SuppressWarnings("deprecation")
  private BootstrapAdmin admin(String email, String password, String clientIds) {
    return new BootstrapAdmin(
        repository, NoOpPasswordEncoder.getInstance(), email, password, clientIds);
  }

  @Test
  void createsOneAdminWhenThereAreNoUsers() {
    when(repository.count()).thenReturn(0L);

    admin("ops@acme.example", "s3cret", "1,2").run();

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(repository).save(saved.capture());
    assertThat(saved.getValue().getEmail()).isEqualTo("ops@acme.example");
    assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
    assertThat(saved.getValue().getClientIds()).containsExactlyInAnyOrder(1L, 2L);
  }

  /**
   * The half that keeps it safe: a restart must not resurrect an account somebody deleted, or reset
   * a password somebody changed.
   */
  @Test
  void doesNothingWhenAccountsAlreadyExist() {
    when(repository.count()).thenReturn(5L);

    admin("ops@acme.example", "s3cret", "1").run();

    verify(repository, never()).save(any());
  }

  /** No shipped default: unconfigured means no account, and a warning rather than a guess. */
  @Test
  void createsNothingWhenUnconfigured() {
    when(repository.count()).thenReturn(0L);

    admin("", "", "1").run();

    verify(repository, never()).save(any());
  }
}
