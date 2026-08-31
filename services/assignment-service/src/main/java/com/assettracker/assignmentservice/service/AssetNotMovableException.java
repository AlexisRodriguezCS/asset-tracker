package com.assettracker.assignmentservice.service;

/** asset-service said the asset is retired or lost. Maps to HTTP 422. */
public class AssetNotMovableException extends RuntimeException {
  public AssetNotMovableException(Long assetId) {
    super("asset " + assetId + " cannot be moved (retired or lost)");
  }
}
