package com.assettracker.locationservice.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An append-only record of one mutating action in this service. Written in the same transaction as
 * the change it describes, so it can never be lost or drift from the data. There is no update or
 * delete path - the row is immutable once persisted.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long clientId;

  /** Who did it - the subject the gateway forwarded (JWT), or {@code system} for service calls. */
  @Column(nullable = false, updatable = false)
  private String actor;

  /** Machine action, e.g. {@code ASSET_RETIRED}. */
  @Column(nullable = false, updatable = false)
  private String action;

  @Column(nullable = false, updatable = false)
  private String entityType;

  @Column(nullable = false, updatable = false)
  private Long entityId;

  /** Human summary, e.g. "retired MacBook Pro 14 (ACME-L-003)". */
  @Column(nullable = false, updatable = false, length = 500)
  private String summary;

  /** Optional JSON with before/after or extra context. */
  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  @Column(updatable = false)
  private String detail;

  @Column(nullable = false, updatable = false)
  private Instant at;

  protected AuditEvent() {
    // for JPA
  }

  public AuditEvent(
      Long clientId,
      String actor,
      String action,
      String entityType,
      Long entityId,
      String summary,
      String detail) {
    this.clientId = clientId;
    this.actor = (actor == null || actor.isBlank()) ? "system" : actor;
    this.action = action;
    this.entityType = entityType;
    this.entityId = entityId;
    this.summary = summary;
    this.detail = detail;
    this.at = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Long getClientId() {
    return clientId;
  }

  public String getActor() {
    return actor;
  }

  public String getAction() {
    return action;
  }

  public String getEntityType() {
    return entityType;
  }

  public Long getEntityId() {
    return entityId;
  }

  public String getSummary() {
    return summary;
  }

  public String getDetail() {
    return detail;
  }

  public Instant getAt() {
    return at;
  }
}
