package com.assettracker.assignmentservice.events;

/**
 * The request asks for more than the client has free that day.
 *
 * <p>A 409, not a 400: the request is well formed and would have been fine on another date or
 * before someone else booked the same gear. The message names every line that does not fit, so the
 * requester can fix the whole form at once rather than discovering the next problem on resubmit.
 */
public class EventEquipmentUnavailableException extends RuntimeException {

  public EventEquipmentUnavailableException(String message) {
    super(message);
  }
}
