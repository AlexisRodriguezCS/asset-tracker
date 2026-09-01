package com.assettracker.assignmentservice.bootstrap;

import com.assettracker.assignmentservice.audit.AuditService;
import com.assettracker.assignmentservice.client.AssetClient;
import com.assettracker.assignmentservice.client.AssetClient.Deployed;
import com.assettracker.assignmentservice.entity.Assignment;
import com.assettracker.assignmentservice.entity.HolderType;
import com.assettracker.assignmentservice.repository.AssignmentRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Opens a custody episode for every asset {@code asset-service} reports as deployed, so the
 * console's custody history is populated on a fresh dev stack. It asks asset-service directly
 * (rather than mirroring a hard-coded id map), retrying while the stack warms up.
 */
@Component
@Profile("!prod")
@Order(100)
public class AssignmentSeeder implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(AssignmentSeeder.class);
  private static final String SEED_ACTOR = "system@seed";
  private static final long[] CLIENT_IDS = {1L, 2L, 3L};
  private static final int MAX_ATTEMPTS = 20;
  private static final long RETRY_DELAY_MS = 3000L;

  private final AssignmentRepository repository;
  private final AssetClient assetClient;
  private final AuditService audit;

  public AssignmentSeeder(
      AssignmentRepository repository, AssetClient assetClient, AuditService audit) {
    this.repository = repository;
    this.assetClient = assetClient;
    this.audit = audit;
  }

  @Override
  public void run(String... args) {
    if (repository.count() > 0) {
      return;
    }
    for (long clientId : CLIENT_IDS) {
      List<Deployed> deployed = fetchWithRetry(clientId);
      deployed.forEach(d -> open(clientId, d));
    }
  }

  private List<Deployed> fetchWithRetry(long clientId) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        List<Deployed> deployed = assetClient.deployedAssets(clientId);
        if (!deployed.isEmpty()) {
          return deployed;
        }
      } catch (RuntimeException ex) {
        log.debug("seed: asset-service not ready for client {} (try {})", clientId, attempt);
      }
      sleep();
    }
    log.warn(
        "seed: gave up waiting for asset-service; client {} custody history left empty", clientId);
    return List.of();
  }

  private void open(long clientId, Deployed d) {
    HolderType holderType = HolderType.valueOf(d.holderType());
    Assignment saved =
        repository.save(
            new Assignment(
                clientId,
                d.assetId(),
                holderType,
                d.holderId(),
                SEED_ACTOR,
                holderType == HolderType.PERSON ? "issued to employee" : "set up at desk"));
    audit.record(
        clientId,
        SEED_ACTOR,
        "ASSET_CHECKED_OUT",
        saved.getId(),
        "checked out asset " + d.assetId() + " to " + holderType + " " + d.holderId(),
        null);
  }

  private static void sleep() {
    try {
      Thread.sleep(RETRY_DELAY_MS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
