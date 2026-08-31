package com.assettracker.assetservice.web.dto;

import com.assettracker.assetservice.entity.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Request body for adding an asset to inventory. */
public record CreateAssetRequest(
    @NotNull Long clientId,
    @NotNull AssetType type,
    String make,
    String model,
    @NotBlank String serialNumber,
    @NotBlank String assetTag,
    LocalDate purchaseDate,
    Long purchaseCostCents,
    String notes) {}
