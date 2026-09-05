package com.assettracker.assignmentservice.events;

import com.assettracker.assignmentservice.audit.AuditService;
import com.assettracker.assignmentservice.entity.HolderType;
import com.assettracker.assignmentservice.events.EventViews.CreateRequest;
import com.assettracker.assignmentservice.events.EventViews.FulfilLine;
import com.assettracker.assignmentservice.events.EventViews.FulfilRequest;
import com.assettracker.assignmentservice.service.AssignmentService;
import com.assettracker.assignmentservice.web.CallerContext;
import com.assettracker.assignmentservice.web.TenantContext;
import com.assettracker.assignmentservice.web.dto.CheckOutRequest;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Event sign-out: an employee asks for gear, a POC or tech decides, a tech hands it out.
 *
 * <p>Fulfilment deliberately goes through {@link AssignmentService#checkOut} rather than writing
 * custody rows itself, so gear signed out for an event is indistinguishable downstream from gear
 * checked out any other way - same asset status, same assignment history, same audit trail, same
 * offboarding sweep picks it up. The request is the paperwork; check-out is still the only thing
 * that moves custody.
 */
@Service
public class EventRequestService {

  private final EventRequestRepository requests;
  private final AssignmentService assignments;
  private final AuditService audit;

  public EventRequestService(
      EventRequestRepository requests, AssignmentService assignments, AuditService audit) {
    this.requests = requests;
    this.assignments = assignments;
    this.audit = audit;
  }

  @Transactional
  public EventRequest create(CreateRequest body, String actor, Long personId) {
    TenantContext.requireAllowed(body.clientId());
    EventRequest request =
        new EventRequest(
            body.clientId(),
            body.eventName().trim(),
            body.eventDate(),
            blankToNull(body.location()),
            blankToNull(body.notes()),
            actor,
            personId);
    body.lines()
        .forEach(
            l ->
                request.addLine(
                    new EventRequestLine(
                        l.itemType().trim(), l.quantity(), blankToNull(l.notes()))));

    EventRequest saved = requests.save(request);
    audit.record(
        saved.getClientId(),
        actor,
        "EVENT_REQUEST_SUBMITTED",
        saved.getId(),
        actor + " requested gear for " + saved.getEventName(),
        null);
    return saved;
  }

  /**
   * The list behind every event view, narrowed to the caller: an ordinary employee sees only the
   * requests they raised, everyone else sees their tenant's.
   */
  @Transactional(readOnly = true)
  public List<EventRequest> list(Long clientId, EventRequestStatus status) {
    TenantContext.requireAllowed(clientId);
    if (CallerContext.isSelfServiceUser()) {
      Long self = CallerContext.personId();
      return self == null
          ? List.of()
          : requests.findByClientIdAndRequesterPersonIdOrderByEventDateDesc(clientId, self);
    }
    return status == null
        ? requests.findByClientIdOrderByEventDateDesc(clientId)
        : requests.findByClientIdAndStatusOrderByEventDateDesc(clientId, status);
  }

  @Transactional(readOnly = true)
  public EventRequest getById(Long id) {
    EventRequest request = requests.findById(id).orElseThrow(() -> notFound(id));
    if (!TenantContext.allows(request.getClientId()) || !maySee(request)) {
      // 404 rather than 403 - whether an id exists is itself information the caller lacks
      throw notFound(id);
    }
    return request;
  }

  @Transactional
  public EventRequest decide(Long id, EventRequestStatus outcome, String note, String actor) {
    EventRequest request = getById(id);
    request.decide(outcome, actor, blankToNull(note));
    audit.record(
        request.getClientId(),
        actor,
        outcome == EventRequestStatus.APPROVED ? "EVENT_REQUEST_APPROVED" : "EVENT_REQUEST_DENIED",
        request.getId(),
        actor + " " + outcome.name().toLowerCase() + " " + request.getEventName(),
        null);
    return requests.save(request);
  }

  /**
   * Hands the gear out: every asset named for a line is checked out to the requesting person
   * through the normal custody path, then the line records what went with it.
   */
  @Transactional
  public EventRequest fulfil(Long id, FulfilRequest body, String actor) {
    EventRequest request = getById(id);
    request.requireFulfillable();
    if (request.getRequesterPersonId() == null) {
      throw new EventRequestStateException(
          "request " + id + " has no requester person record to check gear out to");
    }

    for (FulfilLine line : body.lines()) {
      EventRequestLine target =
          request.getLines().stream()
              .filter(l -> l.getId().equals(line.lineId()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "line " + line.lineId() + " is not on request " + id));
      for (Long assetId : line.assetIds()) {
        assignments.checkOut(
            new CheckOutRequest(
                request.getClientId(),
                assetId,
                HolderType.PERSON,
                request.getRequesterPersonId(),
                "Event: " + request.getEventName()),
            actor);
      }
      target.setFulfilledAssetIds(
          line.assetIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

    request.markFulfilled();
    audit.record(
        request.getClientId(),
        actor,
        "EVENT_REQUEST_FULFILLED",
        request.getId(),
        actor + " handed out gear for " + request.getEventName(),
        null);
    return requests.save(request);
  }

  private boolean maySee(EventRequest request) {
    if (!CallerContext.isSelfServiceUser()) {
      return true;
    }
    Long self = CallerContext.personId();
    return self != null && self.equals(request.getRequesterPersonId());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static EventRequestNotFoundException notFound(Long id) {
    return new EventRequestNotFoundException(id);
  }
}
