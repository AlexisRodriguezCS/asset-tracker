package com.assettracker.assignmentservice.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The claim/complete/release cycle, against a real database.
 *
 * <p>Against a real one because the mechanism *is* the unique index: two retries arriving together
 * both try to insert, and the database is what decides which one proceeds. A mocked repository
 * would happily let both through and the test would prove the opposite of the truth.
 *
 * <p>{@code NOT_SUPPORTED} because {@code @DataJpaTest} would otherwise wrap each test in a
 * transaction that is rolled back at the end - and escaping exactly that kind of enclosing
 * transaction is the whole reason the service uses {@code REQUIRES_NEW}. Left as it comes, the test
 * fights the behaviour it is meant to prove. The price is real rows, so they are deleted afterwards
 * rather than rolled back.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(IdempotencyService.class)
class IdempotencyServiceTest {

  private static final long ACME = 1L;
  private static final String KEY = "checkout-abc-123";

  @Autowired IdempotencyService service;
  @Autowired IdempotencyRecordRepository records;

  private static final String HASH = IdempotencyService.hash(ACME, 42L, "PERSON", 7L, null);
  private static final String OTHER_HASH = IdempotencyService.hash(ACME, 99L, "PERSON", 7L, null);

  @AfterEach
  void clear() {
    records.deleteAll();
  }

  @Test
  void theFirstCallerWinsTheClaimAndDoesTheWork() {
    assertThat(service.claim(ACME, KEY, HASH)).isEmpty();
    assertThat(records.findByClientIdAndKey(ACME, KEY)).isPresent();
  }

  @Test
  void aRetryWhileTheFirstAttemptIsRunningIsToldToWait() {
    service.claim(ACME, KEY, HASH);

    assertThatThrownBy(() -> service.claim(ACME, KEY, HASH))
        .isInstanceOf(IdempotencyConflictException.class)
        .hasMessageContaining("still in progress");
  }

  @Test
  void aRetryAfterSuccessReplaysTheOriginalAnswer() {
    service.claim(ACME, KEY, HASH);
    service.complete(ACME, KEY, 500L);

    Optional<IdempotencyRecord> replay = service.claim(ACME, KEY, HASH);

    assertThat(replay).isPresent();
    assertThat(replay.get().getAssignmentId()).isEqualTo(500L);
    assertThat(replay.get().isCompleted()).isTrue();
  }

  /**
   * The check that stops a replay answering a question nobody asked: the same key with a different
   * body is a caller bug, and handing back the first assignment would hide it.
   */
  @Test
  void reusingAKeyForADifferentRequestIsRefused() {
    service.claim(ACME, KEY, HASH);
    service.complete(ACME, KEY, 500L);

    assertThatThrownBy(() -> service.claim(ACME, KEY, OTHER_HASH))
        .isInstanceOf(IdempotencyConflictException.class)
        .hasMessageContaining("different request");
  }

  /**
   * Without this a transient downstream failure would wedge the key forever, and every retry would
   * be told "in progress" - a worse outcome than the duplicate the key exists to prevent.
   */
  @Test
  void aFailedAttemptReleasesItsKeySoARetryCanProceed() {
    service.claim(ACME, KEY, HASH);
    service.release(ACME, KEY);

    assertThat(records.findByClientIdAndKey(ACME, KEY)).isEmpty();
    assertThat(service.claim(ACME, KEY, HASH)).isEmpty();
  }

  /** A completed claim is an answer worth keeping; releasing it would throw the answer away. */
  @Test
  void releaseLeavesACompletedClaimAlone() {
    service.claim(ACME, KEY, HASH);
    service.complete(ACME, KEY, 500L);

    service.release(ACME, KEY);

    assertThat(records.findByClientIdAndKey(ACME, KEY)).isPresent();
  }

  @Test
  void keysAreScopedToTheirTenant() {
    service.claim(ACME, KEY, HASH);

    // the same key from another client is a different claim entirely
    assertThat(service.claim(2L, KEY, HASH)).isEmpty();
  }

  @Test
  void theFingerprintIgnoresNothingThatMatters() {
    assertThat(IdempotencyService.hash(1L, 2L, "PERSON", 3L, null))
        .isEqualTo(IdempotencyService.hash(1L, 2L, "PERSON", 3L, null));

    // a different holder is a different request, even under the same key
    assertThat(IdempotencyService.hash(1L, 2L, "PERSON", 3L, null))
        .isNotEqualTo(IdempotencyService.hash(1L, 2L, "PERSON", 4L, null));
  }
}
