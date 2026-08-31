package com.assettracker.locationservice.web.dto;

import com.assettracker.locationservice.entity.LocationKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request body for creating a location. */
public record CreateLocationRequest(
    @NotNull Long clientId,
    @NotNull LocationKind kind,
    @NotBlank String label,
    String building,
    String floor,
    @NotBlank String qrTag) {}
