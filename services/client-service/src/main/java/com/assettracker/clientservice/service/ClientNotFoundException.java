package com.assettracker.clientservice.service;

/** Thrown when a requested client does not exist. */
public class ClientNotFoundException extends RuntimeException {

  public ClientNotFoundException(String message) {
    super(message);
  }
}
