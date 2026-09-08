package com.assettracker.peopleservice.web.dto;

import jakarta.validation.constraints.Email;

/**
 * Request body for correcting a person's details.
 *
 * <p>Every field is optional and null means "leave this alone", so a caller fixing a typo in a name
 * cannot blank the department by omitting it. Department is the exception that needs saying out
 * loud: it is nullable in the record too, so there is no way to distinguish "don't touch it" from
 * "clear it" - an empty string clears it, and null leaves it.
 *
 * <p>Client id is not here. Moving somebody between tenants is not an edit; it would strand their
 * assets, their desk and their login in the old one.
 */
public record UpdatePersonRequest(String fullName, @Email String email, String department) {}
