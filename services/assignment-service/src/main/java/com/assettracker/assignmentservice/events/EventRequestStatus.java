package com.assettracker.assignmentservice.events;

/**
 * The life of an event sign-out request.
 *
 * <pre>
 *   SUBMITTED --approve--> APPROVED --hand out--> FULFILLED --collect--> CLOSED
 *        |
 *        +----deny------> DENIED
 * </pre>
 *
 * <p>APPROVED means someone agreed the gear may go out; FULFILLED means specific assets were
 * actually checked out against it. Keeping those apart is the point - "we said yes" and "the TVs
 * left the stockroom" are different facts, and only the second one moves custody.
 */
public enum EventRequestStatus {
  SUBMITTED,
  APPROVED,
  DENIED,
  FULFILLED,
  CLOSED;

  public boolean isDecided() {
    return this != SUBMITTED;
  }

  public boolean canBeDecided() {
    return this == SUBMITTED;
  }

  public boolean canBeFulfilled() {
    return this == APPROVED;
  }
}
