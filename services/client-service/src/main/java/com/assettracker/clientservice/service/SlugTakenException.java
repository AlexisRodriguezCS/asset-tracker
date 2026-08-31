package com.assettracker.clientservice.service;

/** Thrown when creating a client whose slug is already in use. */
public class SlugTakenException extends RuntimeException {

  public SlugTakenException(String slug) {
    super("Client slug '" + slug + "' is already taken");
  }
}
