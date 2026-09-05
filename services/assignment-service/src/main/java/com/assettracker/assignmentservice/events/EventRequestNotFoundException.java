package com.assettracker.assignmentservice.events;

/** No event request with that id is visible to the caller (HTTP 404). */
public class EventRequestNotFoundException extends RuntimeException {
  public EventRequestNotFoundException(Long id) {
    super("no event request " + id);
  }
}
