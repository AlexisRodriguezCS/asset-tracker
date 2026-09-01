package com.assettracker.assetservice.web.dto;

import com.assettracker.assetservice.entity.Asset;
import java.time.Instant;
import java.time.LocalDate;

/** Response view of an asset. */
public record AssetResponse(
    Long id,
    Long clientId,
    String type,
    String make,
    String model,
    String serialNumber,
    String assetTag,
    String status,
    String condition,
    String category,
    String holderType,
    Long holderId,
    LocalDate purchaseDate,
    LocalDate deployedOn,
    LocalDate warrantyEndsOn,
    Long purchaseCostCents,
    String notes,
    Instant createdAt) {

  public static AssetResponse from(Asset a) {
    return new AssetResponse(
        a.getId(),
        a.getClientId(),
        a.getType().name(),
        a.getMake(),
        a.getModel(),
        a.getSerialNumber(),
        a.getAssetTag(),
        a.getStatus().name(),
        a.getCondition() == null ? null : a.getCondition().name(),
        a.getCategory(),
        a.getHolderType().name(),
        a.getHolderId(),
        a.getPurchaseDate(),
        a.getDeployedOn(),
        a.getWarrantyEndsOn(),
        a.getPurchaseCostCents(),
        a.getNotes(),
        a.getCreatedAt());
  }
}
