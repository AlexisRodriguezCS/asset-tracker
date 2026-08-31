package com.assettracker.assignmentservice.audit;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records audit rows. {@link #record} is called from inside the same transaction as the change, so
 * the audit trail and the data commit together or not at all.
 */
@Service
public class AuditService {

  static final String ENTITY_TYPE = "ASSIGNMENT";

  private final AuditEventRepository repository;

  public AuditService(AuditEventRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void record(
      Long clientId, String actor, String action, Long entityId, String summary, String detail) {
    repository.save(
        new AuditEvent(clientId, actor, action, ENTITY_TYPE, entityId, summary, detail));
  }

  @Transactional(readOnly = true)
  public List<AuditEvent> search(
      Long clientId, Long entityId, String actor, String action, Instant since) {
    return repository.search(clientId, ENTITY_TYPE, entityId, actor, action, since);
  }
}
