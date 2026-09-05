package com.assettracker.assignmentservice.events;

import com.assettracker.assignmentservice.events.EventViews.CreateRequest;
import com.assettracker.assignmentservice.events.EventViews.DecisionRequest;
import com.assettracker.assignmentservice.events.EventViews.FulfilRequest;
import com.assettracker.assignmentservice.events.EventViews.RequestView;
import com.assettracker.assignmentservice.web.CallerContext;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Event sign-out. Anyone signed in may raise a request and see their own; deciding needs an
 * approver (POC / tech / admin) and handing gear out needs a tech, because it moves real custody.
 */
@RestController
@RequestMapping("/assignments/event-requests")
public class EventRequestController {

  private final EventRequestService service;

  public EventRequestController(EventRequestService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RequestView submit(
      @Valid @RequestBody CreateRequest body,
      @RequestHeader(value = "X-User-Id", defaultValue = "system") String actor,
      @RequestHeader(value = "X-Person-Id", required = false) Long personId) {
    return RequestView.from(service.create(body, actor, personId));
  }

  @GetMapping
  public List<RequestView> list(
      @RequestParam Long clientId, @RequestParam(required = false) EventRequestStatus status) {
    return service.list(clientId, status).stream().map(RequestView::from).toList();
  }

  @GetMapping("/{id}")
  public RequestView getById(@PathVariable Long id) {
    return RequestView.from(service.getById(id));
  }

  @PostMapping("/{id}/approve")
  public RequestView approve(
      @PathVariable Long id,
      @RequestBody(required = false) DecisionRequest body,
      @RequestHeader(value = "X-User-Id", defaultValue = "system") String actor) {
    CallerContext.requireApprover();
    return RequestView.from(service.decide(id, EventRequestStatus.APPROVED, note(body), actor));
  }

  @PostMapping("/{id}/deny")
  public RequestView deny(
      @PathVariable Long id,
      @RequestBody(required = false) DecisionRequest body,
      @RequestHeader(value = "X-User-Id", defaultValue = "system") String actor) {
    CallerContext.requireApprover();
    return RequestView.from(service.decide(id, EventRequestStatus.DENIED, note(body), actor));
  }

  @PostMapping("/{id}/fulfil")
  public RequestView fulfil(
      @PathVariable Long id,
      @Valid @RequestBody FulfilRequest body,
      @RequestHeader(value = "X-User-Id", defaultValue = "system") String actor) {
    CallerContext.requireAssetOperator();
    return RequestView.from(service.fulfil(id, body, actor));
  }

  private static String note(DecisionRequest body) {
    return body == null ? null : body.note();
  }
}
