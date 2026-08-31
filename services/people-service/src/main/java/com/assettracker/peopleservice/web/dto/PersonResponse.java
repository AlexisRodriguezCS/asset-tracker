package com.assettracker.peopleservice.web.dto;

import com.assettracker.peopleservice.entity.Person;
import java.time.Instant;

/** Response view of a person. */
public record PersonResponse(
    Long id,
    Long clientId,
    String fullName,
    String email,
    String department,
    Long deskId,
    String status,
    Instant createdAt) {

  public static PersonResponse from(Person p) {
    return new PersonResponse(
        p.getId(),
        p.getClientId(),
        p.getFullName(),
        p.getEmail(),
        p.getDepartment(),
        p.getDeskId(),
        p.getStatus().name(),
        p.getCreatedAt());
  }
}
