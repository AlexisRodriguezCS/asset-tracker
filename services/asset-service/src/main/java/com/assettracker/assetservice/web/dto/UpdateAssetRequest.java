package com.assettracker.assetservice.web.dto;

/** Partial edit of an asset's descriptive fields. Null fields are left unchanged. */
public record UpdateAssetRequest(String make, String model, String notes) {}
