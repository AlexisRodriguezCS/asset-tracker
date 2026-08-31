package com.assettracker.peopleservice.service;

/** Thrown when a requested person does not exist (or is not in the caller's client scope). */
public class PersonNotFoundException extends RuntimeException {

  public PersonNotFoundException(String message) {
    super(message);
  }
}
