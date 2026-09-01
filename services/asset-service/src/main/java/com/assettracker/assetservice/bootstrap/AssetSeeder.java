package com.assettracker.assetservice.bootstrap;

import com.assettracker.assetservice.audit.AuditService;
import com.assettracker.assetservice.entity.Asset;
import com.assettracker.assetservice.entity.AssetCondition;
import com.assettracker.assetservice.entity.AssetStatus;
import com.assettracker.assetservice.entity.AssetType;
import com.assettracker.assetservice.entity.HolderType;
import com.assettracker.assetservice.repository.AssetRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds a realistic spread of assets for every demo client: a stockroom of spare kit, laptops /
 * monitors / docks deployed to people and desks, and a handful in repair / broken / heading for
 * recycling so every status and the building-floor map have something to show (dev only).
 *
 * <p>Person and desk ids referenced in the placements line up with {@code PeopleSeeder} and {@code
 * LocationSeeder} insert order on a fresh database. {@code AssignmentSeeder} in assignment-service
 * mirrors these placements.
 */
@Component
@Profile("!prod")
public class AssetSeeder implements CommandLineRunner {

  private static final String SEED_ACTOR = "system@seed";

  private static final ClientPlan[] PLANS = {acme(), globex(), initech()};

  private final AssetRepository repository;
  private final AuditService audit;

  public AssetSeeder(AssetRepository repository, AuditService audit) {
    this.repository = repository;
    this.audit = audit;
  }

  @Override
  public void run(String... args) {
    if (repository.count() > 0) {
      return;
    }
    for (ClientPlan plan : PLANS) {
      seedClient(plan);
    }
  }

  private void seedClient(ClientPlan plan) {
    Map<String, Integer> seq = new HashMap<>();
    List<Asset> built = new ArrayList<>();
    for (Line line : plan.lines()) {
      addLine(built, plan, line, seq);
    }
    List<Asset> assets = repository.saveAll(built);
    for (Placement p : plan.placements()) {
      place(plan.clientId(), assets.get(p.index() - 1), p);
    }
    for (StatusFix f : plan.fixes()) {
      applyFix(plan.clientId(), assets.get(f.index() - 1), f);
    }
  }

  private void addLine(List<Asset> out, ClientPlan plan, Line line, Map<String, Integer> seq) {
    for (int i = 0; i < line.qty(); i++) {
      int n = seq.merge(line.code(), 1, Integer::sum);
      String suffix = String.format("%03d", n);
      Asset a =
          new Asset(
              plan.clientId(),
              line.type(),
              plan.prefix() + "-" + line.make().toUpperCase() + "-SN" + suffix,
              plan.prefix() + "-" + line.code() + "-" + suffix);
      a.setMake(line.make());
      a.setModel(line.model());
      a.setCondition(AssetCondition.GOOD);
      a.setPurchaseCostCents(line.costCents());
      LocalDate bought = LocalDate.now().minusMonths(6L + n);
      a.setPurchaseDate(bought);
      a.setWarrantyEndsOn(bought.plusYears(3));
      out.add(a);
    }
  }

  private void place(long clientId, Asset a, Placement p) {
    a.setDeployedOn(LocalDate.now().minusDays(20L + p.index()));
    a.assignTo(p.holderType(), p.holderId());
    repository.save(a);
    audit.record(
        clientId,
        SEED_ACTOR,
        "ASSET_ASSIGNED",
        a.getId(),
        "assigned " + describe(a) + " to " + p.holderType() + " " + p.holderId(),
        null);
  }

  private void applyFix(long clientId, Asset a, StatusFix f) {
    AssetStatus before = a.getStatus();
    a.setCondition(f.condition());
    a.setStatus(f.status());
    repository.save(a);
    audit.record(
        clientId,
        SEED_ACTOR,
        "ASSET_STATUS_" + f.status().name(),
        a.getId(),
        "changed " + describe(a) + " status " + before + " -> " + f.status(),
        null);
  }

  private static String describe(Asset a) {
    String makeModel =
        ((a.getMake() == null ? "" : a.getMake())
                + " "
                + (a.getModel() == null ? "" : a.getModel()))
            .trim();
    return (makeModel.isBlank() ? a.getType().name() : makeModel) + " (" + a.getAssetTag() + ")";
  }

  // --- per-client plans -----------------------------------------------------

  private static ClientPlan acme() {
    Line[] lines = {
      new Line(AssetType.LAPTOP, "Apple", "MacBook Pro 14 M3", 6, 249900, "L"),
      new Line(AssetType.LAPTOP, "Dell", "Latitude 7440", 4, 149900, "L"),
      new Line(AssetType.TABLET, "Apple", "iPad Air 11", 5, 79900, "T"),
      new Line(AssetType.MONITOR, "Dell", "U2723QE 27in 4K", 8, 54900, "M"),
      new Line(AssetType.DOCK, "CalDigit", "TS4", 6, 39900, "D"),
      new Line(AssetType.CHARGER, "Apple", "96W USB-C", 10, 7900, "C"),
      new Line(AssetType.CABLE, "Anker", "USB-C 2m", 20, 1900, "K"),
    };
    Placement[] placements = {
      new Placement(1, HolderType.PERSON, 1L),
      new Placement(30, HolderType.PERSON, 1L),
      new Placement(40, HolderType.PERSON, 1L),
      new Placement(16, HolderType.LOCATION, 4L),
      new Placement(24, HolderType.LOCATION, 4L),
      new Placement(2, HolderType.PERSON, 2L),
      new Placement(11, HolderType.PERSON, 2L),
      new Placement(31, HolderType.PERSON, 2L),
      new Placement(41, HolderType.PERSON, 2L),
      new Placement(7, HolderType.PERSON, 3L),
      new Placement(32, HolderType.PERSON, 3L),
      new Placement(17, HolderType.LOCATION, 6L),
      new Placement(25, HolderType.LOCATION, 6L),
      new Placement(8, HolderType.PERSON, 4L),
      new Placement(42, HolderType.PERSON, 4L),
      new Placement(18, HolderType.LOCATION, 10L),
      new Placement(26, HolderType.LOCATION, 10L),
      new Placement(19, HolderType.LOCATION, 14L),
    };
    StatusFix[] fixes = {
      new StatusFix(6, AssetStatus.IN_REPAIR, AssetCondition.FAIR),
      new StatusFix(10, AssetStatus.BROKEN, AssetCondition.DAMAGED),
      new StatusFix(23, AssetStatus.RETIRED, AssetCondition.POOR),
      new StatusFix(29, AssetStatus.PENDING_RECYCLE, AssetCondition.POOR),
      new StatusFix(39, AssetStatus.RECYCLED, AssetCondition.DAMAGED),
      new StatusFix(59, AssetStatus.LOST, AssetCondition.GOOD),
    };
    return new ClientPlan(1L, "ACME", lines, placements, fixes);
  }

  private static ClientPlan globex() {
    Line[] lines = {
      new Line(AssetType.LAPTOP, "Apple", "MacBook Air 13 M3", 4, 149900, "L"),
      new Line(AssetType.LAPTOP, "Lenovo", "ThinkPad T14", 2, 139900, "L"),
      new Line(AssetType.TABLET, "Apple", "iPad 10", 2, 44900, "T"),
      new Line(AssetType.MONITOR, "LG", "27UP850 27in 4K", 4, 44900, "M"),
      new Line(AssetType.DOCK, "Anker", "563 USB-C", 3, 19900, "D"),
      new Line(AssetType.CHARGER, "Apple", "70W USB-C", 5, 5900, "C"),
      new Line(AssetType.CABLE, "Anker", "USB-C 2m", 8, 1900, "K"),
    };
    Placement[] placements = {
      new Placement(1, HolderType.PERSON, 5L),
      new Placement(16, HolderType.PERSON, 5L),
      new Placement(21, HolderType.PERSON, 5L),
      new Placement(9, HolderType.LOCATION, 20L),
      new Placement(13, HolderType.LOCATION, 20L),
      new Placement(2, HolderType.PERSON, 6L),
      new Placement(22, HolderType.PERSON, 6L),
      new Placement(5, HolderType.PERSON, 7L),
      new Placement(17, HolderType.PERSON, 7L),
      new Placement(10, HolderType.LOCATION, 22L),
      new Placement(3, HolderType.PERSON, 8L),
      new Placement(11, HolderType.LOCATION, 24L),
    };
    StatusFix[] fixes = {
      new StatusFix(4, AssetStatus.IN_REPAIR, AssetCondition.FAIR),
      new StatusFix(6, AssetStatus.BROKEN, AssetCondition.DAMAGED),
      new StatusFix(12, AssetStatus.PENDING_RECYCLE, AssetCondition.POOR),
      new StatusFix(28, AssetStatus.LOST, AssetCondition.GOOD),
    };
    return new ClientPlan(2L, "GLBX", lines, placements, fixes);
  }

  private static ClientPlan initech() {
    Line[] lines = {
      new Line(AssetType.LAPTOP, "Dell", "XPS 13", 3, 159900, "L"),
      new Line(AssetType.TABLET, "Samsung", "Galaxy Tab S9", 1, 79900, "T"),
      new Line(AssetType.MONITOR, "Dell", "P2422H 24in", 3, 22900, "M"),
      new Line(AssetType.DOCK, "Dell", "WD19", 2, 18900, "D"),
      new Line(AssetType.CHARGER, "Dell", "65W USB-C", 4, 4900, "C"),
      new Line(AssetType.CABLE, "Anker", "USB-C 2m", 6, 1900, "K"),
    };
    Placement[] placements = {
      new Placement(1, HolderType.PERSON, 9L),
      new Placement(10, HolderType.PERSON, 9L),
      new Placement(5, HolderType.LOCATION, 30L),
      new Placement(8, HolderType.LOCATION, 30L),
      new Placement(2, HolderType.PERSON, 10L),
      new Placement(14, HolderType.PERSON, 10L),
      new Placement(3, HolderType.PERSON, 11L),
      new Placement(6, HolderType.LOCATION, 32L),
    };
    StatusFix[] fixes = {
      new StatusFix(4, AssetStatus.BROKEN, AssetCondition.DAMAGED),
      new StatusFix(7, AssetStatus.RETIRED, AssetCondition.POOR),
      new StatusFix(19, AssetStatus.RECYCLED, AssetCondition.DAMAGED),
    };
    return new ClientPlan(3L, "INTC", lines, placements, fixes);
  }

  private record ClientPlan(
      long clientId, String prefix, Line[] lines, Placement[] placements, StatusFix[] fixes) {}

  private record Line(
      AssetType type, String make, String model, int qty, long costCents, String code) {}

  private record Placement(int index, HolderType holderType, Long holderId) {}

  private record StatusFix(int index, AssetStatus status, AssetCondition condition) {}
}
