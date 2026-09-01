package com.assettracker.assignmentservice.bootstrap;

import com.assettracker.assignmentservice.audit.AuditService;
import com.assettracker.assignmentservice.entity.Assignment;
import com.assettracker.assignmentservice.entity.HolderType;
import com.assettracker.assignmentservice.repository.AssignmentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Opens the custody episodes that match the assets {@code asset-service} seeds as deployed, so the
 * console's custody history has data on a fresh dev stack.
 *
 * <p>This mirrors {@code AssetSeeder}: same placements per client, and {@code assetIdBase} is the
 * running total of the inventory sizes of the earlier clients (ACME 59, GLOBEX 28), because asset
 * ids are a single IDENTITY sequence.
 */
@Component
@Profile("!prod")
public class AssignmentSeeder implements CommandLineRunner {

  private static final String SEED_ACTOR = "system@seed";

  private static final ClientBlock[] BLOCKS = {
    new ClientBlock(
        1L,
        0,
        new Row[] {
          new Row(1, HolderType.PERSON, 1L),
          new Row(30, HolderType.PERSON, 1L),
          new Row(40, HolderType.PERSON, 1L),
          new Row(16, HolderType.LOCATION, 4L),
          new Row(24, HolderType.LOCATION, 4L),
          new Row(2, HolderType.PERSON, 2L),
          new Row(11, HolderType.PERSON, 2L),
          new Row(31, HolderType.PERSON, 2L),
          new Row(41, HolderType.PERSON, 2L),
          new Row(7, HolderType.PERSON, 3L),
          new Row(32, HolderType.PERSON, 3L),
          new Row(17, HolderType.LOCATION, 6L),
          new Row(25, HolderType.LOCATION, 6L),
          new Row(8, HolderType.PERSON, 4L),
          new Row(42, HolderType.PERSON, 4L),
          new Row(18, HolderType.LOCATION, 10L),
          new Row(26, HolderType.LOCATION, 10L),
          new Row(19, HolderType.LOCATION, 14L),
        }),
    new ClientBlock(
        2L,
        59,
        new Row[] {
          new Row(1, HolderType.PERSON, 5L),
          new Row(16, HolderType.PERSON, 5L),
          new Row(21, HolderType.PERSON, 5L),
          new Row(9, HolderType.LOCATION, 20L),
          new Row(13, HolderType.LOCATION, 20L),
          new Row(2, HolderType.PERSON, 6L),
          new Row(22, HolderType.PERSON, 6L),
          new Row(5, HolderType.PERSON, 7L),
          new Row(17, HolderType.PERSON, 7L),
          new Row(10, HolderType.LOCATION, 22L),
          new Row(3, HolderType.PERSON, 8L),
          new Row(11, HolderType.LOCATION, 24L),
        }),
    new ClientBlock(
        3L,
        87,
        new Row[] {
          new Row(1, HolderType.PERSON, 9L),
          new Row(10, HolderType.PERSON, 9L),
          new Row(5, HolderType.LOCATION, 30L),
          new Row(8, HolderType.LOCATION, 30L),
          new Row(2, HolderType.PERSON, 10L),
          new Row(14, HolderType.PERSON, 10L),
          new Row(3, HolderType.PERSON, 11L),
          new Row(6, HolderType.LOCATION, 32L),
        }),
  };

  private final AssignmentRepository repository;
  private final AuditService audit;

  public AssignmentSeeder(AssignmentRepository repository, AuditService audit) {
    this.repository = repository;
    this.audit = audit;
  }

  @Override
  public void run(String... args) {
    if (repository.count() > 0) {
      return;
    }
    for (ClientBlock block : BLOCKS) {
      for (Row r : block.rows()) {
        long assetId = block.assetIdBase() + r.index();
        Assignment saved =
            repository.save(
                new Assignment(
                    block.clientId(),
                    assetId,
                    r.holderType(),
                    r.holderId(),
                    SEED_ACTOR,
                    r.holderType() == HolderType.PERSON ? "issued to employee" : "set up at desk"));
        audit.record(
            block.clientId(),
            SEED_ACTOR,
            "ASSET_CHECKED_OUT",
            saved.getId(),
            "checked out asset " + assetId + " to " + r.holderType() + " " + r.holderId(),
            null);
      }
    }
  }

  private record ClientBlock(long clientId, int assetIdBase, Row[] rows) {}

  private record Row(int index, HolderType holderType, Long holderId) {}
}
