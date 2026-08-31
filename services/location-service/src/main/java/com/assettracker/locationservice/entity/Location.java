package com.assettracker.locationservice.entity;

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
 * A place within a client where assets can live - a site, a room, or a desk. Each carries a short
 * {@code qrTag} that a future mobile app scans to pull up "what's here". "What's on desk X" is a
 * query against asset-service (assets whose holder is this location), not stored here.
 */
@Entity
@Table(name = "locations")
public class Location {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long clientId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LocationKind kind;

  @Column(nullable = false)
  private String label;

  private String building;

  private String floor;

  /** Short scan code printed on a sticker (e.g. {@code ACME-D-014}). Unique per client. */
  @Column(nullable = false)
  private String qrTag;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  protected Location() {
    // for JPA
  }

  public Location(Long clientId, LocationKind kind, String label, String qrTag) {
    this.clientId = clientId;
    this.kind = kind;
    this.label = label;
    this.qrTag = qrTag;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Long getClientId() {
    return clientId;
  }

  public LocationKind getKind() {
    return kind;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getBuilding() {
    return building;
  }

  public void setBuilding(String building) {
    this.building = building;
  }

  public String getFloor() {
    return floor;
  }

  public void setFloor(String floor) {
    this.floor = floor;
  }

  public String getQrTag() {
    return qrTag;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
