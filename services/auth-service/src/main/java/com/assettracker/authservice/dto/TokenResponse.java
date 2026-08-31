package com.assettracker.authservice.dto;

/** Issued access token and its metadata. */
public record TokenResponse(String token, String tokenType, long expiresInMs) {

  public static TokenResponse bearer(String token, long expiresInMs) {
    return new TokenResponse(token, "Bearer", expiresInMs);
  }
}
