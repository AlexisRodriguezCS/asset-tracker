package com.assettracker.assetservice.type;

/** The client already has a type with this name (case-insensitive). Maps to HTTP 409. */
public class AssetTypeExistsException extends RuntimeException {

  public AssetTypeExistsException(String name) {
    super("type '" + name + "' already exists for this client");
  }
}
