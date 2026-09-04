package com.assettracker.assetservice.web.dto;

import com.assettracker.assetservice.entity.Asset;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

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
    String holderType,
    Long holderId,
    LocalDate purchaseDate,
    LocalDate deployedOn,
    LocalDate warrantyEndsOn,
    Long purchaseCostCents,
    String notes,
    Long supersedesAssetId,
    Map<String, String> attributes,
    Instant createdAt) {

  public static AssetResponse from(Asset a) {
    return new AssetResponse(
        a.getId(),
        a.getClientId(),
        a.getType(),
        a.getMake(),
        a.getModel(),
        a.getSerialNumber(),
        a.getAssetTag(),
        a.getStatus().name(),
        a.getCondition() == null ? null : a.getCondition().name(),
        a.getHolderType().name(),
        a.getHolderId(),
        a.getPurchaseDate(),
        a.getDeployedOn(),
        a.getWarrantyEndsOn(),
        a.getPurchaseCostCents(),
        a.getNotes(),
        a.getSupersedesAssetId(),
        a.getAttributes(),
        a.getCreatedAt());
  }
}
