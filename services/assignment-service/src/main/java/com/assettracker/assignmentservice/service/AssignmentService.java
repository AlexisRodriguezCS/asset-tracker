package com.assettracker.assignmentservice.service;

import com.assettracker.assignmentservice.audit.AuditDetail;
import com.assettracker.assignmentservice.audit.AuditService;
import com.assettracker.assignmentservice.client.AssetClient;
import com.assettracker.assignmentservice.entity.Assignment;
import com.assettracker.assignmentservice.idempotency.IdempotencyRecord;
import com.assettracker.assignmentservice.idempotency.IdempotencyService;
import com.assettracker.assignmentservice.messaging.NotificationPublisher;
import com.assettracker.assignmentservice.web.CallerContext;
import com.assettracker.assignmentservice.web.TenantContext;
import com.assettracker.assignmentservice.web.dto.CheckOutRequest;
import com.assettracker.assignmentservice.web.dto.OffboardingResult;
import com.assettracker.assignmentservice.web.dto.TransferRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Optional;
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
  private final IdempotencyService idempotency;
  private final AuditService audit;
  private final Counter checkouts;
  private final Counter offboardingRuns;
  private final Counter assetsCollected;
  private final Counter replayedCheckouts;

  public AssignmentService(
      AssetClient assetClient,
      NotificationPublisher notifications,
      AssignmentTransactions store,
      IdempotencyService idempotency,
      AuditService audit,
      MeterRegistry meters) {
    this.assetClient = assetClient;
    this.notifications = notifications;
    this.store = store;
    this.idempotency = idempotency;
    this.audit = audit;
    this.checkouts = meters.counter("assettracker.checkouts");
    this.offboardingRuns = meters.counter("assettracker.offboarding.runs");
    this.assetsCollected = meters.counter("assettracker.offboarding.assets.collected");
    this.replayedCheckouts = meters.counter("assettracker.checkouts.replayed");
  }

  /**
   * Check out an asset to a person or location.
   *
   * @throws AssetUnavailableException asset-service returned 409 (already assigned)
   * @throws AssetNotMovableException asset-service returned 422 (retired / lost)
   */
  public Assignment checkOut(CheckOutRequest request, String actor) {
    return checkOut(request, actor, null);
  }

  /**
   * Check out an asset, optionally under an idempotency key.
   *
   * <p>With a key, a retry of the same request replays the original assignment instead of doing the
   * work twice. Without one the behaviour is exactly as before, so every existing caller -
   * including the event sign-out fulfilment path - is unaffected.
   *
   * @param idempotencyKey from the {@code Idempotency-Key} header, or null
   */
  public Assignment checkOut(CheckOutRequest request, String actor, String idempotencyKey) {
    CallerContext.requireAssetOperator();
    TenantContext.requireAllowed(request.clientId());

    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return performCheckOut(request, actor);
    }

    String key = idempotencyKey.trim();
    String fingerprint =
        IdempotencyService.hash(
            request.clientId(),
            request.assetId(),
            request.holderType(),
            request.holderId(),
            request.note());

    Optional<IdempotencyRecord> alreadyDone =
        idempotency.claim(request.clientId(), key, fingerprint);
    if (alreadyDone.isPresent()) {
      replayedCheckouts.increment();
      log.info("replaying check-out for Idempotency-Key {}", key);
      return store.getById(alreadyDone.get().getAssignmentId());
    }

    try {
      Assignment assignment = performCheckOut(request, actor);
      idempotency.complete(request.clientId(), key, assignment.getId());
      return assignment;
    } catch (RuntimeException failed) {
      // the claim must not outlive the attempt, or every later retry is told
      // "already in progress" and the request is stuck for good
      idempotency.release(request.clientId(), key);
      throw failed;
    }
  }

  private Assignment performCheckOut(CheckOutRequest request, String actor) {
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
    checkouts.increment();
    return assignment;
  }

  /** Return an asset to the stockroom and close its open assignment. */
  public Assignment checkIn(Long assetId, String actor) {
    CallerContext.requireCollector();
    assetClient.returnToStock(assetId, actor);
    Assignment closed = store.close(assetId, actor);
    notifications.publish(
        closed.getClientId(), "ASSET_RETURNED", "Asset " + assetId + " returned to stock");
    return closed;
  }

  /** Return from the current holder, then check out to a new one. */
  public Assignment transfer(TransferRequest request, String actor) {
    CallerContext.requireAssetOperator();
    TenantContext.requireAllowed(request.clientId());
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
   * abort the rest; the result says what came back, what did not, and what came back without being
   * recorded.
   */
  public OffboardingResult offboardPerson(Long clientId, Long personId, String actor) {
    CallerContext.requireCollector();
    TenantContext.requireAllowed(clientId);
    List<Long> assetIds = assetClient.assetsHeldByPerson(clientId, personId);
    OffboardingResult result = new OffboardingResult(personId);
    for (Long assetId : assetIds) {
      collect(assetId, actor, result);
    }
    offboardingRuns.increment();
    assetsCollected.increment(result.returned().size());
    String summary =
        "ran offboarding for person "
            + personId
            + ": "
            + result.returned().size()
            + " collected, "
            + result.failed().size()
            + " outstanding"
            + (result.unrecorded().isEmpty()
                ? ""
                : ", " + result.unrecorded().size() + " collected but not recorded");
    audit.record(
        clientId,
        actor,
        "OFFBOARDING_RUN",
        personId,
        summary,
        AuditDetail.of(
            "returned",
            result.returned(),
            "failed",
            result.failed(),
            "unrecorded",
            result.unrecorded()));
    notifications.publish(clientId, "OFFBOARDING_COLLECTED", summary);
    return result;
  }

  /**
   * One asset's half of an offboarding sweep, in the order the two systems actually change.
   *
   * <p>The return is a call to asset-service and the close is a local write, and they used to sit
   * in one try block - so a close that failed after a successful return reported the asset as still
   * out. It was on the shelf. HR chased the employee anyway.
   *
   * <p>They are separated here because the two failures mean opposite things: before the return,
   * nothing has moved and the asset really is with the person; after it, the laptop is back and
   * only the paperwork is wrong. The second is logged at error - it is an inconsistency someone has
   * to repair, not a person to go and find.
   */
  private void collect(Long assetId, String actor, OffboardingResult result) {
    try {
      assetClient.returnToStock(assetId, actor);
    } catch (RuntimeException ex) {
      log.warn("offboarding: asset {} did not return: {}", assetId, ex.getMessage());
      result.failed().add(assetId);
      return;
    }

    try {
      store.close(assetId, actor);
      result.returned().add(assetId);
    } catch (RuntimeException ex) {
      log.error(
          "offboarding: asset {} is back in stock but its assignment did not close: {}",
          assetId,
          ex.getMessage());
      result.unrecorded().add(assetId);
    }
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
