package com.assettracker.assetservice.web.dto;

import com.assettracker.assetservice.entity.AssetCondition;
import java.time.LocalDate;

/** Partial edit of an asset's descriptive fields. Null fields are left unchanged. */
public record UpdateAssetRequest(
    String make,
    String model,
    String notes,
    AssetCondition condition,
    LocalDate deployedOn,
    LocalDate warrantyEndsOn) {}
