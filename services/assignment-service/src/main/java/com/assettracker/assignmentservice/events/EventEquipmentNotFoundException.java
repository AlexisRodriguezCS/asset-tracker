package com.assettracker.assignmentservice.events;

/** No event equipment row with that id. */
public class EventEquipmentNotFoundException extends RuntimeException {

  public EventEquipmentNotFoundException(Long id) {
    super("No event equipment with id " + id);
  }
}
