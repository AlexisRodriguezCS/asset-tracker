package com.assettracker.peopleservice.entity;

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
 * An employee of a client company who may hold assets. Not every person has a desk - {@code deskId}
 * is a soft reference (id in location-service) and is nullable.
 */
@Entity
@Table(name = "people")
public class Person {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** The tenant this person belongs to (id in client-service). */
  @Column(nullable = false)
  private Long clientId;

  @Column(nullable = false)
  private String fullName;

  @Column(nullable = false)
  private String email;

  private String department;

  /** Optional home desk (id in location-service). Many people have none. */
  private Long deskId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PersonStatus status;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  protected Person() {
    // for JPA
  }

  public Person(Long clientId, String fullName, String email, String department) {
    this.clientId = clientId;
    this.fullName = fullName;
    this.email = email;
    this.department = department;
    this.status = PersonStatus.ACTIVE;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Long getClientId() {
    return clientId;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getEmail() {
    return email;
  }

  public String getDepartment() {
    return department;
  }

  public void setDepartment(String department) {
    this.department = department;
  }

  public Long getDeskId() {
    return deskId;
  }

  public void setDeskId(Long deskId) {
    this.deskId = deskId;
  }

  public PersonStatus getStatus() {
    return status;
  }

  public void setStatus(PersonStatus status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
