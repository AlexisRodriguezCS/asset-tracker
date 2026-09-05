package com.assettracker.assignmentservice.events;

/** A request was asked to make a transition its current status does not allow (HTTP 409). */
public class EventRequestStateException extends RuntimeException {
  public EventRequestStateException(String message) {
    super(message);
  }
}
