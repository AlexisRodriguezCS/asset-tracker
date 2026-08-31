package com.assettracker.peopleservice.audit;

import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of this service's audit trail. There is no write endpoint - rows are only ever
 * created as a side effect of a real change.
 */
@RestController
@RequestMapping("/people/audit")
public class AuditController {

  private final AuditService service;

  public AuditController(AuditService service) {
    this.service = service;
  }

  @GetMapping
  public List<AuditView> list(
      @RequestParam Long clientId,
      @RequestParam(required = false) Long entityId,
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) Instant since) {
    return service.search(clientId, entityId, actor, action, since).stream()
        .map(AuditView::from)
        .toList();
  }

  /** Response view of an audit event. */
  public record AuditView(
      Long id,
      Long clientId,
      String actor,
      String action,
      String entityType,
      Long entityId,
      String summary,
      String detail,
      Instant at) {

    static AuditView from(AuditEvent e) {
      return new AuditView(
          e.getId(),
          e.getClientId(),
          e.getActor(),
          e.getAction(),
          e.getEntityType(),
          e.getEntityId(),
          e.getSummary(),
          e.getDetail(),
          e.getAt());
    }
  }
}
