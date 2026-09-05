package com.assettracker.authservice.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A registered user of the console. Carries the authorization facts that end up in the JWT: a
 * {@link Role} and the set of client (tenant) ids the user may act on.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String password;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role = Role.TECH;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_client_ids", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "client_id", nullable = false)
  private Set<Long> clientIds = new HashSet<>();

  /**
   * The people-service person this login belongs to, when the user is an employee rather than
   * platform staff. Carried in the JWT so downstream services can scope "what is assigned to me"
   * without a lookup; null for staff logins that map to no employee record.
   */
  @Column(name = "person_id")
  private Long personId;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public User(String email, String password, Role role, Set<Long> clientIds) {
    this.email = email;
    this.password = password;
    this.role = role;
    this.clientIds = new HashSet<>(clientIds);
  }

  public User(String email, String password, Role role, Set<Long> clientIds, Long personId) {
    this(email, password, role, clientIds);
    this.personId = personId;
  }
}
