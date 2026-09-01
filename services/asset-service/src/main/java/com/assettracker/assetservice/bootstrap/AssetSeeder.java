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
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds a realistic fleet for every demo client (dev only):
 *
 * <ul>
 *   <li>a person kit - laptop plus a charger and cable that <b>share the laptop's tag</b> - for
 *       every seeded employee;
 *   <li>a desk kit - monitor, dock and Thunderbolt cable sharing the desk's tag - at every home /
 *       hot desk;
 *   <li>loose stock, some of it in repair / broken / heading for recycling;
 *   <li>a couple of retired accessories that still carry a laptop's tag, to show the
 *       retire-and-replace pattern (the live unit and the dead one share one tag, one per type).
 * </ul>
 *
 * <p>Person and desk ids match {@code PeopleSeeder} / {@code LocationSeeder} insert order.
 */
@Component
@Profile("!prod")
public class AssetSeeder implements CommandLineRunner {

  private static final String SEED_ACTOR = "system@seed";

  private static final Gear MBP14 = new Gear("Apple", "MacBook Pro 14 M3", 249900);
  private static final Gear MBA13 = new Gear("Apple", "MacBook Air 13 M3", 149900);
  private static final Gear LAT = new Gear("Dell", "Latitude 7440", 149900);
  private static final Gear TP14 = new Gear("Lenovo", "ThinkPad T14 Gen 5", 139900);
  private static final Gear XPS13 = new Gear("Dell", "XPS 13 9340", 159900);
  private static final Gear IPAD_AIR = new Gear("Apple", "iPad Air 11", 79900);
  private static final Gear IPAD10 = new Gear("Apple", "iPad 10", 44900);
  private static final Gear GTAB = new Gear("Samsung", "Galaxy Tab S9", 79900);
  private static final Gear DELL_MON = new Gear("Dell", "U2723QE 27in 4K", 54900);
  private static final Gear LG_MON = new Gear("LG", "27UP850 27in 4K", 44900);
  private static final Gear DELL_P24 = new Gear("Dell", "P2422H 24in", 22900);
  private static final Gear TS4 = new Gear("CalDigit", "TS4 Dock", 39900);
  private static final Gear ANKER_DOCK = new Gear("Anker", "563 USB-C Dock", 19900);
  private static final Gear DELL_WD19 = new Gear("Dell", "WD19 Dock", 18900);
  private static final Gear CHG96 = new Gear("Apple", "96W USB-C Charger", 7900);
  private static final Gear CHG70 = new Gear("Apple", "70W USB-C Charger", 5900);
  private static final Gear CHG65 = new Gear("Dell", "65W USB-C Charger", 4900);
  private static final Gear USBC = new Gear("Anker", "USB-C 2m Cable", 1900);
  private static final Gear TBC = new Gear("CalDigit", "Thunderbolt 4 2m Cable", 4900);
  private static final Gear HS = new Gear("Netgear", "Nighthawk M6 5G Hotspot", 59900);

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
    seedClient(1L, "ACME", AssetSeeder::acme);
    seedClient(2L, "GLBX", AssetSeeder::globex);
    seedClient(3L, "INTC", AssetSeeder::initech);
  }

  private void seedClient(
      long clientId, String prefix, java.util.function.Consumer<List<Row>> plan) {
    List<Row> rows = new ArrayList<>();
    plan.accept(rows);

    List<Asset> built = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      built.add(baseAsset(clientId, rows.get(i), i));
    }
    List<Asset> saved = repository.saveAll(built);

    for (int i = 0; i < rows.size(); i++) {
      applyState(clientId, saved.get(i), rows.get(i), i);
    }
  }

  private Asset baseAsset(long clientId, Row r, int seq) {
    String serial = r.tag() + "-" + r.type().name().charAt(0) + "-" + String.format("%04d", seq);
    Asset a = new Asset(clientId, r.type(), serial, r.tag());
    a.setMake(r.gear().make());
    a.setModel(r.gear().model());
    a.setPurchaseCostCents(r.gear().costCents());
    a.setCondition(r.condition());
    a.setCategory(defaultCategory(r.type()));
    LocalDate bought = LocalDate.now().minusMonths(8);
    a.setPurchaseDate(bought);
    a.setWarrantyEndsOn(bought.plusYears(3));
    return a;
  }

  /** A starter category per kind - techs edit these freely and add their own. */
  private static String defaultCategory(AssetType t) {
    return switch (t) {
      case LAPTOP -> "Standard build";
      case TABLET -> "Field device";
      case PHONE -> "Mobile";
      case MONITOR, DOCK -> "Desk kit";
      case CHARGER, CABLE -> "Accessory pool";
      case HOTSPOT -> "Travel kit";
      default -> null;
    };
  }

  private void applyState(long clientId, Asset a, Row r, int seq) {
    if (r.status() == AssetStatus.ASSIGNED) {
      a.setDeployedOn(LocalDate.now().minusDays(15L + seq));
      a.assignTo(r.holderType(), r.holderId());
      repository.save(a);
      audit.record(
          clientId,
          SEED_ACTOR,
          "ASSET_ASSIGNED",
          a.getId(),
          "assigned " + describe(a) + " to " + r.holderType() + " " + r.holderId(),
          null);
      return;
    }
    if (r.status() != AssetStatus.IN_STOCK) {
      a.setStatus(r.status());
      repository.save(a);
      audit.record(
          clientId,
          SEED_ACTOR,
          "ASSET_STATUS_" + r.status().name(),
          a.getId(),
          "changed " + describe(a) + " status IN_STOCK -> " + r.status(),
          null);
    }
  }

  // --- per-client plans ---------------------------------------------------

  private static void acme(List<Row> out) {
    personKit(out, "ACME", 1, 1L, MBP14, CHG96, USBC);
    personKit(out, "ACME", 2, 2L, MBP14, CHG96, USBC);
    personKit(out, "ACME", 3, 3L, LAT, CHG96, USBC);
    personKit(out, "ACME", 4, 4L, LAT, CHG96, USBC);
    deskKit(out, "ACME", 1, 4L, DELL_MON, TS4, TBC);
    deskKit(out, "ACME", 3, 6L, DELL_MON, TS4, TBC);
    deskKit(out, "ACME", 7, 10L, DELL_MON, TS4, TBC);
    deskKit(out, "ACME", 11, 14L, DELL_MON, TS4, TBC);
    deskKit(out, "ACME", 13, 16L, DELL_MON, TS4, TBC);
    deskKit(out, "ACME", 19, 22L, DELL_MON, TS4, TBC);
    deskKit(out, "ACME", 24, 27L, DELL_MON, TS4, TBC);
    stock(out, "ACME", "L", AssetType.LAPTOP, MBP14, 4, 5);
    stock(out, "ACME", "T", AssetType.TABLET, IPAD_AIR, 3, 1);
    stock(out, "ACME", "MON", AssetType.MONITOR, DELL_MON, 4, 1);
    stock(out, "ACME", "DCK", AssetType.DOCK, TS4, 3, 1);
    stock(out, "ACME", "CHG", AssetType.CHARGER, CHG96, 6, 1);
    stock(out, "ACME", "CBL", AssetType.CABLE, USBC, 10, 1);
    hotspot(out, "ACME", 1, 1L);
    hotspot(out, "ACME", 2, 3L);
    stock(out, "ACME", "HS", AssetType.HOTSPOT, HS, 2, 3);
    retire(out, "ACME-L-001", AssetType.CHARGER, CHG96, AssetStatus.LOST);
    retire(out, "ACME-L-002", AssetType.CABLE, USBC, AssetStatus.RETIRED);
    fix(out, "ACME-L-005", AssetStatus.IN_REPAIR, AssetCondition.FAIR);
    fix(out, "ACME-MON-001", AssetStatus.BROKEN, AssetCondition.DAMAGED);
    fix(out, "ACME-DCK-001", AssetStatus.PENDING_RECYCLE, AssetCondition.POOR);
    fix(out, "ACME-CHG-001", AssetStatus.RECYCLED, AssetCondition.DAMAGED);
  }

  private static void globex(List<Row> out) {
    personKit(out, "GLBX", 1, 5L, MBA13, CHG70, USBC);
    personKit(out, "GLBX", 2, 6L, MBA13, CHG70, USBC);
    personKit(out, "GLBX", 3, 7L, TP14, CHG70, USBC);
    personKit(out, "GLBX", 4, 8L, MBA13, CHG70, USBC);
    deskKit(out, "GLBX", 1, 34L, LG_MON, ANKER_DOCK, TBC);
    deskKit(out, "GLBX", 3, 36L, LG_MON, ANKER_DOCK, TBC);
    deskKit(out, "GLBX", 5, 38L, LG_MON, ANKER_DOCK, TBC);
    deskKit(out, "GLBX", 7, 40L, LG_MON, ANKER_DOCK, TBC);
    deskKit(out, "GLBX", 12, 45L, LG_MON, ANKER_DOCK, TBC);
    stock(out, "GLBX", "L", AssetType.LAPTOP, MBA13, 3, 5);
    stock(out, "GLBX", "T", AssetType.TABLET, IPAD10, 2, 1);
    stock(out, "GLBX", "MON", AssetType.MONITOR, LG_MON, 3, 1);
    stock(out, "GLBX", "DCK", AssetType.DOCK, ANKER_DOCK, 2, 1);
    stock(out, "GLBX", "CHG", AssetType.CHARGER, CHG70, 4, 1);
    stock(out, "GLBX", "CBL", AssetType.CABLE, USBC, 6, 1);
    hotspot(out, "GLBX", 1, 5L);
    hotspot(out, "GLBX", 2, 7L);
    stock(out, "GLBX", "HS", AssetType.HOTSPOT, HS, 1, 3);
    retire(out, "GLBX-L-001", AssetType.CHARGER, CHG70, AssetStatus.LOST);
    fix(out, "GLBX-L-005", AssetStatus.IN_REPAIR, AssetCondition.FAIR);
    fix(out, "GLBX-MON-001", AssetStatus.BROKEN, AssetCondition.DAMAGED);
    fix(out, "GLBX-DCK-001", AssetStatus.PENDING_RECYCLE, AssetCondition.POOR);
  }

  private static void initech(List<Row> out) {
    personKit(out, "INTC", 1, 9L, XPS13, CHG65, USBC);
    personKit(out, "INTC", 2, 10L, XPS13, CHG65, USBC);
    personKit(out, "INTC", 3, 11L, XPS13, CHG65, USBC);
    deskKit(out, "INTC", 1, 50L, DELL_P24, DELL_WD19, TBC);
    deskKit(out, "INTC", 3, 52L, DELL_P24, DELL_WD19, TBC);
    deskKit(out, "INTC", 5, 54L, DELL_P24, DELL_WD19, TBC);
    stock(out, "INTC", "L", AssetType.LAPTOP, XPS13, 2, 4);
    stock(out, "INTC", "T", AssetType.TABLET, GTAB, 1, 1);
    stock(out, "INTC", "MON", AssetType.MONITOR, DELL_P24, 2, 1);
    stock(out, "INTC", "DCK", AssetType.DOCK, DELL_WD19, 1, 1);
    stock(out, "INTC", "CHG", AssetType.CHARGER, CHG65, 3, 1);
    stock(out, "INTC", "CBL", AssetType.CABLE, USBC, 4, 1);
    hotspot(out, "INTC", 1, 9L);
    stock(out, "INTC", "HS", AssetType.HOTSPOT, HS, 1, 2);
    retire(out, "INTC-L-001", AssetType.CABLE, USBC, AssetStatus.RETIRED);
    fix(out, "INTC-MON-001", AssetStatus.BROKEN, AssetCondition.DAMAGED);
    fix(out, "INTC-CBL-001", AssetStatus.RECYCLED, AssetCondition.DAMAGED);
  }

  // --- row builders -----------------------------------------------------

  private static void personKit(
      List<Row> out,
      String pfx,
      int laptopNo,
      long personId,
      Gear laptop,
      Gear charger,
      Gear cable) {
    String tag = pfx + "-L-" + n3(laptopNo);
    out.add(Row.deployed(AssetType.LAPTOP, laptop, tag, HolderType.PERSON, personId));
    out.add(Row.deployed(AssetType.CHARGER, charger, tag, HolderType.PERSON, personId));
    out.add(Row.deployed(AssetType.CABLE, cable, tag, HolderType.PERSON, personId));
  }

  private static void deskKit(
      List<Row> out,
      String pfx,
      int deskNo,
      long deskLocId,
      Gear monitor,
      Gear dock,
      Gear tbCable) {
    String tag = pfx + "-D-" + n3(deskNo);
    out.add(Row.deployed(AssetType.MONITOR, monitor, tag, HolderType.LOCATION, deskLocId));
    out.add(Row.deployed(AssetType.DOCK, dock, tag, HolderType.LOCATION, deskLocId));
    out.add(Row.deployed(AssetType.CABLE, tbCable, tag, HolderType.LOCATION, deskLocId));
  }

  private static void stock(
      List<Row> out, String pfx, String code, AssetType type, Gear gear, int qty, int startNo) {
    for (int i = 0; i < qty; i++) {
      out.add(Row.stocked(type, gear, pfx + "-" + code + "-" + n3(startNo + i)));
    }
  }

  /**
   * A 5G hotspot issued to a person - shows the tech-added HOTSPOT type / "Travel kit" category.
   */
  private static void hotspot(List<Row> out, String pfx, int no, long personId) {
    out.add(
        Row.deployed(AssetType.HOTSPOT, HS, pfx + "-HS-" + n3(no), HolderType.PERSON, personId));
  }

  /**
   * A dead accessory that still carries a laptop's tag (its live replacement is in a person kit).
   */
  private static void retire(List<Row> out, String tag, AssetType type, Gear gear, AssetStatus s) {
    out.add(new Row(type, gear, tag, s, null, null, AssetCondition.POOR));
  }

  /** Pulls an already-added stock row out of service - no new asset, just a status change. */
  private static void fix(List<Row> out, String tag, AssetStatus s, AssetCondition c) {
    for (int i = 0; i < out.size(); i++) {
      Row r = out.get(i);
      if (r.tag().equals(tag) && r.status() == AssetStatus.IN_STOCK) {
        out.set(i, new Row(r.type(), r.gear(), tag, s, null, null, c));
        return;
      }
    }
    throw new IllegalStateException("no in-stock row to pull for tag " + tag);
  }

  private static String n3(int n) {
    return String.format("%03d", n);
  }

  private static String describe(Asset a) {
    String makeModel =
        ((a.getMake() == null ? "" : a.getMake())
                + " "
                + (a.getModel() == null ? "" : a.getModel()))
            .trim();
    return (makeModel.isBlank() ? a.getType().name() : makeModel) + " (" + a.getAssetTag() + ")";
  }

  private record Gear(String make, String model, long costCents) {}

  private record Row(
      AssetType type,
      Gear gear,
      String tag,
      AssetStatus status,
      HolderType holderType,
      Long holderId,
      AssetCondition condition) {

    static Row deployed(AssetType type, Gear gear, String tag, HolderType ht, Long hid) {
      return new Row(type, gear, tag, AssetStatus.ASSIGNED, ht, hid, AssetCondition.GOOD);
    }

    static Row stocked(AssetType type, Gear gear, String tag) {
      return new Row(type, gear, tag, AssetStatus.IN_STOCK, null, null, AssetCondition.GOOD);
    }
  }
}
