package com.assettracker.notificationservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Body for recording a notification. */
public record NotificationRequest(
    @NotNull Long clientId, @NotBlank String type, @NotBlank String message) {}
