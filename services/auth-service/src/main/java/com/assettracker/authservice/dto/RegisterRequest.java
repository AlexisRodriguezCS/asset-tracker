package com.assettracker.authservice.dto;

import com.assettracker.authservice.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Registration payload. {@code role} defaults to {@code TECH} and {@code clientIds} to empty; both
 * are normally set by an admin, not chosen at self-signup.
 */
public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password,
    Role role,
    List<Long> clientIds) {}
