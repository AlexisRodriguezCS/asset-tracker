package com.assettracker.clientservice.entity;

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
 * A tenant - one of the client companies whose assets we track. Every asset, person and location in
 * the platform belongs to exactly one client; JWTs carry the set of client ids a user may act on.
 */
@Entity
@Table(name = "clients")
public class Client {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  /** URL-safe stable key (e.g. {@code acme}); used in tokens and paths. */
  @Column(nullable = false, unique = true)
  private String slug;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ClientStatus status;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  protected Client() {
    // for JPA
  }

  public Client(String name, String slug) {
    this.name = name;
    this.slug = slug;
    this.status = ClientStatus.ACTIVE;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSlug() {
    return slug;
  }

  public ClientStatus getStatus() {
    return status;
  }

  public void setStatus(ClientStatus status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
