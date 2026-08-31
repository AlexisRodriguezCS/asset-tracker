package com.assettracker.assignmentservice.entity;

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
 * One custody episode: an asset was handed to a person or location at a point in time and later
 * returned. Rows are append-only - a return stamps {@code returnedAt}, it never deletes. An asset
 * has at most one open (un-returned) assignment.
 */
@Entity
@Table(name = "assignments")
public class Assignment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long clientId;

  @Column(nullable = false)
  private Long assetId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private HolderType holderType;

  @Column(nullable = false)
  private Long holderId;

  /** Identity of the tech who performed the check-out (subject from the JWT). */
  @Column(nullable = false)
  private String checkedOutBy;

  @Column(nullable = false, updatable = false)
  private Instant checkedOutAt;

  private Instant returnedAt;

  private String returnedBy;

  private String note;

  protected Assignment() {
    // for JPA
  }

  public Assignment(
      Long clientId,
      Long assetId,
      HolderType holderType,
      Long holderId,
      String checkedOutBy,
      String note) {
    this.clientId = clientId;
    this.assetId = assetId;
    this.holderType = holderType;
    this.holderId = holderId;
    this.checkedOutBy = checkedOutBy;
    this.note = note;
    this.checkedOutAt = Instant.now();
  }

  public void markReturned(String returnedBy) {
    this.returnedAt = Instant.now();
    this.returnedBy = returnedBy;
  }

  public boolean isOpen() {
    return returnedAt == null;
  }

  public Long getId() {
    return id;
  }

  public Long getClientId() {
    return clientId;
  }

  public Long getAssetId() {
    return assetId;
  }

  public HolderType getHolderType() {
    return holderType;
  }

  public Long getHolderId() {
    return holderId;
  }

  public String getCheckedOutBy() {
    return checkedOutBy;
  }

  public Instant getCheckedOutAt() {
    return checkedOutAt;
  }

  public Instant getReturnedAt() {
    return returnedAt;
  }

  public String getReturnedBy() {
    return returnedBy;
  }

  public String getNote() {
    return note;
  }
}
