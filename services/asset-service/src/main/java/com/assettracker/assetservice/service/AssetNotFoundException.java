package com.assettracker.assetservice.service;

/** Thrown when a requested asset does not exist. Maps to HTTP 404. */
public class AssetNotFoundException extends RuntimeException {

  public AssetNotFoundException(String message) {
    super(message);
  }
}
