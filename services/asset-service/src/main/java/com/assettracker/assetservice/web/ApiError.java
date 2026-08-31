package com.assettracker.assetservice.web;

import java.time.Instant;

/** Uniform error body returned by this service. */
public record ApiError(int status, String code, String message, Instant timestamp) {

  public static ApiError of(int status, String code, String message) {
    return new ApiError(status, code, message, Instant.now());
  }
}
