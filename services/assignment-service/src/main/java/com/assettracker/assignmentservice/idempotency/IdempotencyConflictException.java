package com.assettracker.assignmentservice.idempotency;

/**
 * The key has been seen before, but not in a way that can be replayed.
 *
 * <p>Either it was used for a different request - which is a caller bug worth surfacing loudly,
 * since replaying the first answer would answer a question nobody asked - or the original attempt
 * is still running, and the honest response is "ask again shortly", not a second check-out.
 */
public class IdempotencyConflictException extends RuntimeException {

  public IdempotencyConflictException(String message) {
    super(message);
  }
}
