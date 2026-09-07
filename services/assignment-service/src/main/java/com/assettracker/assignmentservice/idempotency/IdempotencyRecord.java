package com.assettracker.assignmentservice.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One client's use of one idempotency key.
 *
 * <p>Reserved before the work starts, not written after it. That ordering is the whole point: two
 * retries arriving together both try to insert, the unique index lets exactly one through, and the
 * loser is told the work is in flight instead of doing it a second time. A record written after
 * success would leave that window wide open.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

  /** How far a reservation gets. */
  public enum State {
    /** Reserved; the work is running now. */
    IN_PROGRESS,
    /** The work finished, and {@code assignmentId} is the answer to replay. */
    COMPLETED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long clientId;

  @Column(name = "idem_key", nullable = false, length = 120)
  private String key;

  @Column(nullable = false, length = 64)
  private String requestHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private State status = State.IN_PROGRESS;

  private Long assignmentId;

  @Column(nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  private Instant completedAt;

  protected IdempotencyRecord() {}

  public IdempotencyRecord(Long clientId, String key, String requestHash) {
    this.clientId = clientId;
    this.key = key;
    this.requestHash = requestHash;
  }

  public void complete(Long assignmentId) {
    this.assignmentId = assignmentId;
    this.status = State.COMPLETED;
    this.completedAt = Instant.now();
  }

  public boolean isCompleted() {
    return status == State.COMPLETED;
  }

  public boolean matches(String hash) {
    return requestHash.equals(hash);
  }

  public Long getId() {
    return id;
  }

  public Long getClientId() {
    return clientId;
  }

  public String getKey() {
    return key;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public State getStatus() {
    return status;
  }

  public Long getAssignmentId() {
    return assignmentId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
