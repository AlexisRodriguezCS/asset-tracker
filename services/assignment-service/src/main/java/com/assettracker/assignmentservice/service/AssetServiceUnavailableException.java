package com.assettracker.assignmentservice.service;

/**
 * asset-service could not be reached after retries, or its circuit breaker is open. Distinct from
 * {@link AssetUnavailableException} (a 409 business outcome) - this one means an outage, and maps
 * to HTTP 503.
 */
public class AssetServiceUnavailableException extends RuntimeException {

  public AssetServiceUnavailableException(Throwable cause) {
    super("asset-service is unavailable", cause);
  }

  public AssetServiceUnavailableException(Long assetId, Throwable cause) {
    super("asset-service is unavailable (asset " + assetId + ")", cause);
  }
}
