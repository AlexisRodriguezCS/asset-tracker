package com.assettracker.assignmentservice.events;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A request to sign gear out for an event - "Career Fair, Oct 3: a loaner laptop, 2 TVs, a
 * speaker". Raised by any signed-in employee, decided by a POC or tech, then fulfilled by a tech
 * who attaches the actual assets.
 *
 * <p>The event itself is captured on the request (name, date, where) rather than being a separate
 * entity people have to create first: the requester types what they know, in one form.
 */
@Entity
@Table(name = "event_requests")
public class EventRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long clientId;

  @Column(nullable = false, length = 160)
  private String eventName;

  @Column(nullable = false)
  private LocalDate eventDate;

  @Column(length = 160)
  private String location;

  @Column(length = 1000)
  private String notes;

  /** The email of whoever raised it, from the gateway's forwarded identity. */
  @Column(nullable = false, length = 255)
  private String requestedBy;

  /** The requester's people-service record, when the login maps to one. */
  private Long requesterPersonId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EventRequestStatus status = EventRequestStatus.SUBMITTED;

  @Column(length = 255)
  private String decidedBy;

  private Instant decidedAt;

  @Column(length = 500)
  private String decisionNote;

  @Column(nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(name = "request_id")
  private List<EventRequestLine> lines = new ArrayList<>();

  protected EventRequest() {}

  public EventRequest(
      Long clientId,
      String eventName,
      LocalDate eventDate,
      String location,
      String notes,
      String requestedBy,
      Long requesterPersonId) {
    this.clientId = clientId;
    this.eventName = eventName;
    this.eventDate = eventDate;
    this.location = location;
    this.notes = notes;
    this.requestedBy = requestedBy;
    this.requesterPersonId = requesterPersonId;
  }

  public void addLine(EventRequestLine line) {
    lines.add(line);
  }

  /** Approve or deny; only a request nobody has ruled on yet can be decided. */
  public void decide(EventRequestStatus outcome, String actor, String note) {
    if (!status.canBeDecided()) {
      throw new EventRequestStateException(
          "request " + id + " is already " + status + " and cannot be decided again");
    }
    if (outcome != EventRequestStatus.APPROVED && outcome != EventRequestStatus.DENIED) {
      throw new IllegalArgumentException("a decision is APPROVED or DENIED, not " + outcome);
    }
    this.status = outcome;
    this.decidedBy = actor;
    this.decidedAt = Instant.now();
    this.decisionNote = note;
  }

  /**
   * Guards the fulfilment transition. Called before any gear moves, not just at the end: handing
   * out assets is a side effect on other aggregates, and a request that was never approved must not
   * get halfway through checking laptops out before anyone notices.
   */
  public void requireFulfillable() {
    if (!status.canBeFulfilled()) {
      throw new EventRequestStateException(
          "request " + id + " is " + status + "; only an APPROVED request can be fulfilled");
    }
  }

  /** Marks the gear as actually handed out; only an approved request can be fulfilled. */
  public void markFulfilled() {
    requireFulfillable();
    this.status = EventRequestStatus.FULFILLED;
  }

  /** Everything came back. */
  public void close() {
    this.status = EventRequestStatus.CLOSED;
  }

  public Long getId() {
    return id;
  }

  public Long getClientId() {
    return clientId;
  }

  public String getEventName() {
    return eventName;
  }

  public LocalDate getEventDate() {
    return eventDate;
  }

  public String getLocation() {
    return location;
  }

  public String getNotes() {
    return notes;
  }

  public String getRequestedBy() {
    return requestedBy;
  }

  public Long getRequesterPersonId() {
    return requesterPersonId;
  }

  public EventRequestStatus getStatus() {
    return status;
  }

  public String getDecidedBy() {
    return decidedBy;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public String getDecisionNote() {
    return decisionNote;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public List<EventRequestLine> getLines() {
    return lines;
  }
}
