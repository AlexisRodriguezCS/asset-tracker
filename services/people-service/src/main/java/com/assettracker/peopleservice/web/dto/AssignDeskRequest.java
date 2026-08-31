package com.assettracker.peopleservice.web.dto;

/** Body for setting or clearing a person's home desk. {@code deskId} null clears it. */
public record AssignDeskRequest(Long deskId) {}
