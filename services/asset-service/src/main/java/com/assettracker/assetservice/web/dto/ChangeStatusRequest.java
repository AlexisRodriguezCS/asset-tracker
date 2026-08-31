package com.assettracker.assetservice.web.dto;

import com.assettracker.assetservice.entity.AssetStatus;
import jakarta.validation.constraints.NotNull;

/** Body for retiring / repairing / marking-lost an asset. */
public record ChangeStatusRequest(@NotNull AssetStatus status) {}
