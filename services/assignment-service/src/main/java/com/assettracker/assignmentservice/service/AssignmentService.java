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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  private static final ObjectMapper JSON = new ObjectMapper();

  private final AssetClient assetClient;
  private final NotificationPublisher notifications;
  private final AssignmentTransactions store;
  private final IdempotencyService idempotency;
  private final AuditService audit;
  private final Counter checkouts;
  private final Counter offboardingRuns;
  private final Counter assetsCollected;
  private final Counter replayedCheckouts;
  private final Counter replayedTransfers;
  private final Counter replayedOffboardings;

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
    this.replayedTransfers = meters.counter("assettracker.transfers.replayed");
    this.replayedOffboardings = meters.counter("assettracker.offboarding.replayed");
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
    return transfer(request, actor, null);
  }

  /**
   * Transfer an asset, optionally under an idempotency key.
   *
   * <p>It needs one more than check-out does, not less: a transfer is a return followed by a
   * check-out, so a retry that arrives after the return has landed finds the asset in stock and
   * moves it again - or fails half way and leaves it in the stockroom, which is not where either
   * holder expects it. With a key the second delivery replays the first answer.
   */
  public Assignment transfer(TransferRequest request, String actor, String idempotencyKey) {
    CallerContext.requireAssetOperator();
    TenantContext.requireAllowed(request.clientId());

    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return performTransfer(request, actor);
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
      replayedTransfers.increment();
      log.info("replaying transfer for Idempotency-Key {}", key);
      return store.getById(alreadyDone.get().getAssignmentId());
    }

    try {
      Assignment moved = performTransfer(request, actor);
      idempotency.complete(request.clientId(), key, moved.getId());
      return moved;
    } catch (RuntimeException failed) {
      idempotency.release(request.clientId(), key);
      throw failed;
    }
  }

  private Assignment performTransfer(TransferRequest request, String actor) {
    checkIn(request.assetId(), actor);
    // no key on the inner check-out: the outer one already covers the whole move
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
    return offboardPerson(clientId, personId, actor, null);
  }

  /**
   * Run an offboarding sweep, optionally under an idempotency key.
   *
   * <p>The stored answer is replayed rather than recomputed, which matters more here than for a
   * check-out: a repeated sweep finds the assets already back and would honestly report "0
   * collected", which is not what the first call said and not what the caller retrying a timeout is
   * asking for.
   */
  public OffboardingResult offboardPerson(
      Long clientId, Long personId, String actor, String idempotencyKey) {
    CallerContext.requireCollector();
    TenantContext.requireAllowed(clientId);

    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return sweep(clientId, personId, actor);
    }

    String key = idempotencyKey.trim();
    Optional<IdempotencyRecord> alreadyDone =
        idempotency.claim(clientId, key, IdempotencyService.hash(clientId, personId));
    if (alreadyDone.isPresent()) {
      replayedOffboardings.increment();
      log.info("replaying offboarding for Idempotency-Key {}", key);
      return readResult(alreadyDone.get().getResultJson());
    }

    try {
      OffboardingResult result = sweep(clientId, personId, actor);
      idempotency.completeWith(clientId, key, writeResult(result));
      return result;
    } catch (RuntimeException failed) {
      idempotency.release(clientId, key);
      throw failed;
    }
  }

  private OffboardingResult sweep(Long clientId, Long personId, String actor) {
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
  /**
   * The sweep result as JSON, for replay.
   *
   * <p>A private mapper rather than an injected one so the service stays constructible without a
   * Spring context - the same choice {@code AuditDetail} makes for the same reason.
   */
  private static String writeResult(OffboardingResult result) {
    try {
      return JSON.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("offboarding result could not be stored for replay", e);
    }
  }

  private static OffboardingResult readResult(String json) {
    try {
      return JSON.readValue(json, OffboardingResult.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("stored offboarding result could not be replayed", e);
    }
  }

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
