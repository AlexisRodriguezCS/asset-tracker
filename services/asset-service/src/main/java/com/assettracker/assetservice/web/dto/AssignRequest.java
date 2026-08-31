package com.assettracker.assetservice.web.dto;

import com.assettracker.assetservice.entity.HolderType;
import jakarta.validation.constraints.NotNull;

/** Internal: set an asset's holder. Called by assignment-service. */
public record AssignRequest(@NotNull HolderType holderType, Long holderId) {}
