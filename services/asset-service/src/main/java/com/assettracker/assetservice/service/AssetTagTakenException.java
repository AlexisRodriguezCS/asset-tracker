package com.assettracker.assetservice.service;

/** Thrown when creating an asset whose tag is already in use. Maps to HTTP 409. */
public class AssetTagTakenException extends RuntimeException {

  public AssetTagTakenException(String assetTag) {
    super("asset tag '" + assetTag + "' is already in use");
  }
}
