package com.assettracker.peopleservice.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request body for adding a person. */
public record CreatePersonRequest(
    @NotNull Long clientId,
    @NotBlank String fullName,
    @NotBlank @Email String email,
    String department,
    Long deskId) {}
