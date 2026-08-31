package com.assettracker.assetservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A single physical thing we track for a client. Its {@code status} and {@code holder} are the live
 * custody state; the full history lives in assignment-service. Guarded transitions ({@link
 * #assignTo}, {@link #returnToStock}, {@link #setStatus}) are what give assignment-service its 409
 * / 422 failure paths.
 */
@Entity
@Table(name = "assets")
public class Asset {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long clientId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AssetType type;

  private String make;

  private String model;

  @Column(nullable = false)
  private String serialNumber;

  /** Short scan code on a sticker; unique per client. */
  @Column(nullable = false)
  private String assetTag;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AssetStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private HolderType holderType;

  /** id of the person or location holding it; null when in the stockroom. */
  private Long holderId;

  private LocalDate purchaseDate;

  private Long purchaseCostCents;

  private String notes;

  @Version private long version;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  protected Asset() {
    // for JPA
  }

  public Asset(Long clientId, AssetType type, String serialNumber, String assetTag) {
    this.clientId = clientId;
    this.type = type;
    this.serialNumber = serialNumber;
    this.assetTag = assetTag;
    this.status = AssetStatus.IN_STOCK;
    this.holderType = HolderType.STOCKROOM;
    this.createdAt = Instant.now();
  }

  /** Places the asset with a person or location. Rejects if it is not free to move. */
  public void assignTo(HolderType newHolderType, Long newHolderId) {
    if (status == AssetStatus.RETIRED || status == AssetStatus.LOST) {
      throw new IllegalStateException("asset is " + status);
    }
    if (status == AssetStatus.ASSIGNED) {
      throw new AlreadyAssignedException(id, holderType, holderId);
    }
    this.holderType = newHolderType;
    this.holderId = newHolderId;
    this.status = AssetStatus.ASSIGNED;
  }

  /** Returns the asset to the stockroom. */
  public void returnToStock() {
    if (status == AssetStatus.RETIRED || status == AssetStatus.LOST) {
      throw new IllegalStateException("asset is " + status);
    }
    this.holderType = HolderType.STOCKROOM;
    this.holderId = null;
    this.status = AssetStatus.IN_STOCK;
  }

  public void setStatus(AssetStatus newStatus) {
    if (newStatus == AssetStatus.RETIRED || newStatus == AssetStatus.LOST) {
      this.holderType = HolderType.STOCKROOM;
      this.holderId = null;
    }
    this.status = newStatus;
  }

  public Long getId() {
    return id;
  }

  public Long getClientId() {
    return clientId;
  }

  public AssetType getType() {
    return type;
  }

  public String getMake() {
    return make;
  }

  public void setMake(String make) {
    this.make = make;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getSerialNumber() {
    return serialNumber;
  }

  public String getAssetTag() {
    return assetTag;
  }

  public AssetStatus getStatus() {
    return status;
  }

  public HolderType getHolderType() {
    return holderType;
  }

  public Long getHolderId() {
    return holderId;
  }

  public LocalDate getPurchaseDate() {
    return purchaseDate;
  }

  public void setPurchaseDate(LocalDate purchaseDate) {
    this.purchaseDate = purchaseDate;
  }

  public Long getPurchaseCostCents() {
    return purchaseCostCents;
  }

  public void setPurchaseCostCents(Long purchaseCostCents) {
    this.purchaseCostCents = purchaseCostCents;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
