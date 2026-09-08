package com.assettracker.locationservice.service;

/** Thrown when a location cannot be deleted because equipment is still placed on it. */
public class LocationOccupiedException extends RuntimeException {

  public LocationOccupiedException(String label, int placed) {
    super(label + " still has " + placed + " item" + (placed == 1 ? "" : "s") + " on it");
  }
}
