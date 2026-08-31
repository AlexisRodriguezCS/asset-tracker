package com.assettracker.assignmentservice.service;

/** asset-service said the asset is already assigned. Maps to HTTP 409. */
public class AssetUnavailableException extends RuntimeException {
  public AssetUnavailableException(Long assetId) {
    super("asset " + assetId + " is already assigned");
  }
}
