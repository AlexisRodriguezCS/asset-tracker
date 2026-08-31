package com.assettracker.assignmentservice.service;

/** Tried to return an asset that is not currently checked out. Maps to HTTP 409. */
public class NoOpenAssignmentException extends RuntimeException {
  public NoOpenAssignmentException(Long assetId) {
    super("asset " + assetId + " has no open assignment");
  }
}
