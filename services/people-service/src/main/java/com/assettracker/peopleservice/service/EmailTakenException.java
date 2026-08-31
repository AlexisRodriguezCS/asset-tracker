package com.assettracker.peopleservice.service;

/** Thrown when a person with the same email already exists for that client. */
public class EmailTakenException extends RuntimeException {

  public EmailTakenException(String email) {
    super("A person with email '" + email + "' already exists for this client");
  }
}
