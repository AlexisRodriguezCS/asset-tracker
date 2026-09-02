package com.assettracker.assetservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * A kind of thing a client tracks (Laptop, Monitor, Hotspot, ...). Managed by the client's techs:
 * they add types (rejected if the name is already taken) and remove them (blocked while assets
 * still reference the name, unless those assets are reassigned to another type first).
 *
 * <p>{@code Asset.type} stores the name, not a foreign key, so a delete never cascades to assets.
 */
@Entity
@Table(
    name = "asset_types",
    uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "name"}))
public class AssetType {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long clientId;

  @Column(nullable = false, length = 64)
  private String name;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  protected AssetType() {
    // for JPA
  }

  public AssetType(Long clientId, String name) {
    this.clientId = clientId;
    this.name = name;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Long getClientId() {
    return clientId;
  }

  public String getName() {
    return name;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
