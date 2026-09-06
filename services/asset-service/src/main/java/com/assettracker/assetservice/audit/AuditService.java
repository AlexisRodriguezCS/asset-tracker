package com.assettracker.assetservice.audit;

import com.assettracker.assetservice.web.CallerContext;
import com.assettracker.assetservice.web.TenantContext;
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

  /** Every row this service writes describes an asset; the reports rollups filter on it too. */
  public static final String ENTITY_TYPE = "ASSET";

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

  /**
   * The audit trail for one tenant.
   *
   * <p>Scoped two ways, because it was not scoped at all: a caller could read any client's history
   * by passing its id, and an ordinary employee could read who moved what across the whole
   * organisation. The trail is a staff record - it says which technician touched which asset - so
   * employees get nothing rather than a filtered view, and the tenant check is the same one every
   * other read uses.
   */
  @Transactional(readOnly = true)
  public List<AuditEvent> search(
      Long clientId, Long entityId, String actor, String action, Instant since) {
    TenantContext.requireAllowed(clientId);
    if (CallerContext.isSelfServiceUser()) {
      return List.of();
    }
    return repository.search(clientId, ENTITY_TYPE, entityId, actor, action, since);
  }
}
