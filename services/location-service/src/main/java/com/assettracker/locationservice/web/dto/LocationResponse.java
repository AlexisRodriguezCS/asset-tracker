package com.assettracker.locationservice.web.dto;

import com.assettracker.locationservice.entity.Location;
import java.time.Instant;

/** Response view of a location. */
public record LocationResponse(
    Long id,
    Long clientId,
    String kind,
    String label,
    String building,
    String floor,
    String qrTag,
    Instant createdAt) {

  public static LocationResponse from(Location l) {
    return new LocationResponse(
        l.getId(),
        l.getClientId(),
        l.getKind().name(),
        l.getLabel(),
        l.getBuilding(),
        l.getFloor(),
        l.getQrTag(),
        l.getCreatedAt());
  }
}
