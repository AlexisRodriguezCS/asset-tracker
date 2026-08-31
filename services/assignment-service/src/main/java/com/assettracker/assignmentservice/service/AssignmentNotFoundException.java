package com.assettracker.assignmentservice.service;

/** Maps to HTTP 404. */
public class AssignmentNotFoundException extends RuntimeException {
  public AssignmentNotFoundException(String message) {
    super(message);
  }
}
