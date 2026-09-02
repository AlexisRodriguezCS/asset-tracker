package com.assettracker.authservice.dto;

import jakarta.validation.constraints.NotBlank;

/** The Microsoft Entra ID id-token obtained by the console's OIDC callback. */
public record MicrosoftTokenRequest(@NotBlank String idToken) {}
