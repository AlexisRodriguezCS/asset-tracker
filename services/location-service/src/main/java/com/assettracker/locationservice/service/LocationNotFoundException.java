package com.assettracker.locationservice.service;

/** Thrown when a requested location does not exist. */
public class LocationNotFoundException extends RuntimeException {

  public LocationNotFoundException(String message) {
    super(message);
  }
}
