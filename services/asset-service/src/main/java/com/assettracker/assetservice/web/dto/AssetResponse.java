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
    String holderType,
    Long holderId,
    LocalDate purchaseDate,
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
        a.getHolderType().name(),
        a.getHolderId(),
        a.getPurchaseDate(),
        a.getPurchaseCostCents(),
        a.getNotes(),
        a.getCreatedAt());
  }
}
