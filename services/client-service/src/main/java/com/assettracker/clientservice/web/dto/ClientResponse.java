package com.assettracker.clientservice.web.dto;

import com.assettracker.clientservice.entity.Client;
import java.time.Instant;

/** Response view of a client. */
public record ClientResponse(Long id, String name, String slug, String status, Instant createdAt) {

  public static ClientResponse from(Client c) {
    return new ClientResponse(
        c.getId(), c.getName(), c.getSlug(), c.getStatus().name(), c.getCreatedAt());
  }
}
