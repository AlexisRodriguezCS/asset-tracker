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

  /** The type name, one of the client's managed {@link AssetType} rows. */
  @Column(nullable = false, length = 64)
  private String type;

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

  @Enumerated(EnumType.STRING)
  private AssetCondition condition;

  private LocalDate purchaseDate;

  /** When the asset was first put into service / handed out. */
  private LocalDate deployedOn;

  /** Manufacturer or vendor warranty expiry. */
  private LocalDate warrantyEndsOn;

  private Long purchaseCostCents;

  private String notes;

  @Version private long version;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  protected Asset() {
    // for JPA
  }

  public Asset(Long clientId, String type, String serialNumber, String assetTag) {
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
    if (isEndOfLife(status)) {
      throw new IllegalStateException("asset is " + status);
    }
    if (status == AssetStatus.ASSIGNED) {
      throw new AlreadyAssignedException(id, holderType, holderId);
    }
    this.holderType = newHolderType;
    this.holderId = newHolderId;
    this.status = AssetStatus.ASSIGNED;
    if (deployedOn == null) {
      this.deployedOn = LocalDate.now();
    }
  }

  /** Returns the asset to the stockroom. */
  public void returnToStock() {
    if (isEndOfLife(status)) {
      throw new IllegalStateException("asset is " + status);
    }
    this.holderType = HolderType.STOCKROOM;
    this.holderId = null;
    this.status = AssetStatus.IN_STOCK;
  }

  public void setStatus(AssetStatus newStatus) {
    if (isEndOfLife(newStatus)) {
      this.holderType = HolderType.STOCKROOM;
      this.holderId = null;
    }
    this.status = newStatus;
  }

  /** End-of-life states cannot be assigned or returned, and carry no holder. */
  private static boolean isEndOfLife(AssetStatus s) {
    return s == AssetStatus.RETIRED
        || s == AssetStatus.LOST
        || s == AssetStatus.RECYCLED
        || s == AssetStatus.PENDING_RECYCLE;
  }

  public Long getId() {
    return id;
  }

  public Long getClientId() {
    return clientId;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
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

  public AssetCondition getCondition() {
    return condition;
  }

  public void setCondition(AssetCondition condition) {
    this.condition = condition;
  }

  public LocalDate getPurchaseDate() {
    return purchaseDate;
  }

  public void setPurchaseDate(LocalDate purchaseDate) {
    this.purchaseDate = purchaseDate;
  }

  public LocalDate getDeployedOn() {
    return deployedOn;
  }

  public void setDeployedOn(LocalDate deployedOn) {
    this.deployedOn = deployedOn;
  }

  public LocalDate getWarrantyEndsOn() {
    return warrantyEndsOn;
  }

  public void setWarrantyEndsOn(LocalDate warrantyEndsOn) {
    this.warrantyEndsOn = warrantyEndsOn;
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
