package com.assettracker.assignmentservice.web.dto;

import com.assettracker.assignmentservice.entity.HolderType;
import jakarta.validation.constraints.NotNull;

/** Body for checking an asset out to a person or a location. */
public record CheckOutRequest(
    @NotNull Long clientId,
    @NotNull Long assetId,
    @NotNull HolderType holderType,
    @NotNull Long holderId,
    String note) {}
