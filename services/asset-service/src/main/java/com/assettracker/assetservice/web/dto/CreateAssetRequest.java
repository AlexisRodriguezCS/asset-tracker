package com.assettracker.assetservice.web.dto;

import com.assettracker.assetservice.entity.AssetCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Request body for adding an asset to inventory. */
public record CreateAssetRequest(
    @NotNull Long clientId,
    @NotBlank String type,
    String make,
    String model,
    @NotBlank String serialNumber,
    @NotBlank String assetTag,
    AssetCondition condition,
    LocalDate purchaseDate,
    LocalDate deployedOn,
    LocalDate warrantyEndsOn,
    Long purchaseCostCents,
    String notes) {}
