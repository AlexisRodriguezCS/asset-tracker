package com.assettracker.assignmentservice.service;

import com.assettracker.assignmentservice.audit.AuditService;
import com.assettracker.assignmentservice.client.AssetClient;
import com.assettracker.assignmentservice.entity.Assignment;
import com.assettracker.assignmentservice.messaging.NotificationPublisher;
import com.assettracker.assignmentservice.web.dto.CheckOutRequest;
import com.assettracker.assignmentservice.web.dto.OffboardingResult;
import com.assettracker.assignmentservice.web.dto.TransferRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates asset custody. Not {@code @Transactional} - the flow spans HTTP calls to
 * asset-service, so persistence is split into the short independent transactions of {@link
 * AssignmentTransactions}.
 */
@Service
public class AssignmentService {

  private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

  private final AssetClient assetClient;
  private final NotificationPublisher notifications;
  private final AssignmentTransactions store;
  private final AuditService audit;

  public AssignmentService(
      AssetClient assetClient,
      NotificationPublisher notifications,
      AssignmentTransactions store,
      AuditService audit) {
    this.assetClient = assetClient;
    this.notifications = notifications;
    this.store = store;
    this.audit = audit;
  }

  /**
   * Check out an asset to a person or location.
   *
   * @throws AssetUnavailableException asset-service returned 409 (already assigned)
   * @throws AssetNotMovableException asset-service returned 422 (retired / lost)
   */
  public Assignment checkOut(CheckOutRequest request, String actor) {
    assetClient.assign(request.assetId(), request.holderType().name(), request.holderId(), actor);

    Assignment assignment =
        store.open(
            request.clientId(),
            request.assetId(),
            request.holderType(),
            request.holderId(),
            actor,
            request.note());

    notifications.publish(
        request.clientId(),
        "ASSET_CHECKED_OUT",
        "Asset "
            + request.assetId()
            + " checked out to "
            + request.holderType()
            + " "
            + request.holderId());
    return assignment;
  }

  /** Return an asset to the stockroom and close its open assignment. */
  public Assignment checkIn(Long assetId, String actor) {
    assetClient.returnToStock(assetId, actor);
    Assignment closed = store.close(assetId, actor);
    notifications.publish(
        closed.getClientId(), "ASSET_RETURNED", "Asset " + assetId + " returned to stock");
    return closed;
  }

  /** Return from the current holder, then check out to a new one. */
  public Assignment transfer(TransferRequest request, String actor) {
    checkIn(request.assetId(), actor);
    return checkOut(
        new CheckOutRequest(
            request.clientId(),
            request.assetId(),
            request.holderType(),
            request.holderId(),
            request.note()),
        actor);
  }

  /**
   * Collect every asset a departing person holds. Best-effort per asset - one stuck return does not
   * abort the rest; the result lists what came back and what did not.
   */
  public OffboardingResult offboardPerson(Long clientId, Long personId, String actor) {
    List<Long> assetIds = assetClient.assetsHeldByPerson(clientId, personId);
    OffboardingResult result = new OffboardingResult(personId);
    for (Long assetId : assetIds) {
      try {
        assetClient.returnToStock(assetId, actor);
        store.close(assetId, actor);
        result.returned().add(assetId);
      } catch (RuntimeException ex) {
        log.warn("offboarding: asset {} did not return: {}", assetId, ex.getMessage());
        result.failed().add(assetId);
      }
    }
    String summary =
        "ran offboarding for person "
            + personId
            + ": "
            + result.returned().size()
            + " collected, "
            + result.failed().size()
            + " outstanding";
    audit.record(
        clientId,
        actor,
        "OFFBOARDING_RUN",
        personId,
        summary,
        "{\"returned\":" + result.returned() + ",\"failed\":" + result.failed() + "}");
    notifications.publish(clientId, "OFFBOARDING_COLLECTED", summary);
    return result;
  }

  public Assignment getById(Long id) {
    return store.getById(id);
  }

  public List<Assignment> byClient(Long clientId) {
    return store.byClient(clientId);
  }

  public List<Assignment> byAsset(Long assetId) {
    return store.byAsset(assetId);
  }

  public List<Assignment> openForPerson(Long personId) {
    return store.openForPerson(personId);
  }
}
