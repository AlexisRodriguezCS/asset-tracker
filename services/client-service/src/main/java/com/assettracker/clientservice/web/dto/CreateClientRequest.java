package com.assettracker.clientservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Request body for creating a client. */
public record CreateClientRequest(
    @NotBlank String name,
    @NotBlank
        @Pattern(
            regexp = "[a-z0-9-]{2,40}",
            message = "must be lower-case letters, digits or hyphens")
        String slug) {}
