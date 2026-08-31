package com.assettracker.notificationservice.domain;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/** An immutable notification record held in memory, scoped to a client (tenant). */
public record Notification(long id, Long clientId, String type, String message, Instant createdAt) {

  private static final AtomicLong SEQUENCE = new AtomicLong();

  public static Notification create(Long clientId, String type, String message) {
    return new Notification(SEQUENCE.incrementAndGet(), clientId, type, message, Instant.now());
  }
}
