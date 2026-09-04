package com.assettracker.assetservice.imports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A saved column-&gt;field mapping so a client's monthly re-upload is one click. */
@Entity
@Table(name = "import_profiles")
public class ImportProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long clientId;

  @Column(nullable = false, length = 120)
  private String name;

  /** JSON of {@link ColumnMapping}. */
  @Column(nullable = false, columnDefinition = "text")
  private String mapping;

  @Column(nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected ImportProfile() {}

  public ImportProfile(Long clientId, String name, String mapping) {
    this.clientId = clientId;
    this.name = name;
    this.mapping = mapping;
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

  public String getMapping() {
    return mapping;
  }

  public void setMapping(String mapping) {
    this.mapping = mapping;
  }
}
