package com.assettracker.assetservice.type;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Body for adding a type name to a client's list. */
public record CreateTypeRequest(@NotNull Long clientId, @NotBlank String name) {}
