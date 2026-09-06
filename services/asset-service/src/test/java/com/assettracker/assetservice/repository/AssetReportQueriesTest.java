package com.assettracker.assetservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.assettracker.assetservice.audit.AuditEvent;
import com.assettracker.assetservice.audit.AuditEventRepository;
import com.assettracker.assetservice.audit.AuditService;
import com.assettracker.assetservice.entity.Asset;
import com.assettracker.assetservice.entity.AssetStatus;
import com.assettracker.assetservice.entity.HolderType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

/**
 * The grouped queries behind the reports page. They exist so the page stops pulling the whole
 * catalog and the whole audit trail to group them in the render - which means the grouping has to
 * be right down here instead, because a rollup that counts the wrong rows looks exactly like one
 * that does not.
 */
@DataJpaTest
class AssetReportQueriesTest {

  private static final long ACME = 1L;
  private static final long GLOBEX = 2L;
  private static final long ALEX = 7L;
  private static final long SAM = 8L;
  private static final long DESK = 3L;

  private static final List<String> INCIDENTS = List.of("ASSET_STATUS_BROKEN", "ASSET_STATUS_LOST");
  private static final List<AssetStatus> IN_ESTATE =
      List.of(AssetStatus.IN_STOCK, AssetStatus.ASSIGNED, AssetStatus.IN_REPAIR);

  @Autowired AssetRepository assets;
  @Autowired AuditEventRepository audit;

  @BeforeEach
  void seed() {
    Asset alexLaptop = held(costing(asset("Laptop", "SN-A", "TAG-A"), 120_000L), ALEX);
    Asset alexCharger = held(costing(asset("Charger", "SN-B", "TAG-A"), 4_000L), ALEX);
    Asset samLaptop = held(costing(asset("Laptop", "SN-C", "TAG-B"), 130_000L), SAM);
    Asset inStock = costing(asset("Laptop", "SN-D", "TAG-C"), 110_000L);

    // broken but still out: BROKEN is not end-of-life, so the holder survives the transition
    Asset brokenLaptop = held(costing(asset("Laptop", "SN-E", "TAG-D"), 100_000L), SAM);
    brokenLaptop.setStatus(AssetStatus.BROKEN);

    Asset brokenCable = costing(asset("Cable", "SN-F", "TAG-E"), 1_500L);
    brokenCable.assignTo(HolderType.LOCATION, DESK);
    brokenCable.setStatus(AssetStatus.BROKEN);

    // lost, however, is end-of-life, and setStatus drops the holder - so a lost unit always lands
    // in the stockroom bucket. The page has always behaved that way; the rollup matches it.
    Asset lostCable = costing(asset("Cable", "SN-G", "TAG-F"), 900L);
    lostCable.setStatus(AssetStatus.LOST);

    // two replacements on one tag, one on another, so "most replaced" has an order to get right
    Asset chargerAgain = supersedes(asset("Charger", "SN-H", "TAG-A"), 1L);
    Asset chargerOnceMore = supersedes(asset("Charger", "SN-I", "TAG-A"), 2L);
    Asset cableAgain = supersedes(asset("Cable", "SN-J", "TAG-E"), 3L);

    Asset otherTenant = costing(new Asset(GLOBEX, "Laptop", "SN-Z", "TAG-Z"), 999_000L);

    assets.saveAll(
        List.of(
            alexLaptop,
            alexCharger,
            samLaptop,
            inStock,
            brokenLaptop,
            brokenCable,
            lostCable,
            chargerAgain,
            chargerOnceMore,
            cableAgain,
            otherTenant));

    audit.saveAll(
        List.of(
            event(ACME, "ASSET_STATUS_BROKEN", brokenLaptop.getId()),
            event(ACME, "ASSET_STATUS_BROKEN", brokenCable.getId()),
            event(ACME, "ASSET_STATUS_LOST", lostCable.getId()),
            event(ACME, "ASSET_ASSIGNED", alexLaptop.getId()),
            event(ACME, "ASSET_ASSIGNED", samLaptop.getId()),
            // an event whose asset no longer exists: the trail outlives what it describes
            event(ACME, "ASSET_STATUS_BROKEN", 9_999L),
            event(GLOBEX, "ASSET_STATUS_BROKEN", otherTenant.getId())));
  }

  @Test
  void fleetValueCountsOnlyWhatIsStillInTheEstate() {
    // the broken laptop, the broken cable and the lost cable are written off; the three
    // replacements carry no cost; the other tenant is not ours
    assertThat(assets.sumPurchaseCost(ACME, IN_ESTATE)).isEqualTo(364_000L);
  }

  @Test
  void assetsPerHolderAreBoundedByHeadcountNotByCatalogSize() {
    assertThat(counts(assets.countByHolder(ACME, HolderType.PERSON)))
        .containsOnly(entry(ALEX, 2L), entry(SAM, 2L));
  }

  @Test
  void holderTypeGivesTheDeskAndStockroomBuckets() {
    assertThat(counts(assets.countByHolderType(ACME)))
        .containsOnly(
            entry(HolderType.PERSON.name(), 4L),
            entry(HolderType.LOCATION.name(), 1L),
            entry(HolderType.STOCKROOM.name(), 5L));
  }

  @Test
  void mostReplacedSlotsComeBackHeaviestFirstAndCapped() {
    List<AssetRepository.Slot> slots = assets.countReplacedSlots(ACME, PageRequest.of(0, 1));

    assertThat(slots).hasSize(1);
    assertThat(slots.get(0).getAssetTag()).isEqualTo("TAG-A");
    assertThat(slots.get(0).getType()).isEqualTo("Charger");
    assertThat(slots.get(0).getTotal()).isEqualTo(2L);
  }

  @Test
  void unratedAndReplacedAreCountedSeparatelyBecauseAGroupByCannotBucketNull() {
    assertThat(assets.countByClientIdAndConditionIsNull(ACME)).isEqualTo(8L);
    assertThat(assets.countByClientIdAndSupersedesAssetIdNotNull(ACME)).isEqualTo(3L);
  }

  @Test
  void lifecycleCountsEveryActionForThisTenantOnly() {
    assertThat(counts(audit.countByAction(ACME, AuditService.ENTITY_TYPE)))
        .containsOnly(
            entry("ASSET_ASSIGNED", 2L),
            entry("ASSET_STATUS_BROKEN", 3L),
            entry("ASSET_STATUS_LOST", 1L));
  }

  /** The join is on an id, not a foreign key, so an event with no surviving asset drops out. */
  @Test
  void incidentsAreAttributedToTheAssetTheyTouched() {
    assertThat(counts(audit.countIncidentsByAssetType(ACME, AuditService.ENTITY_TYPE, INCIDENTS)))
        .containsOnly(entry("Laptop", 1L), entry("Cable", 2L));
  }

  @Test
  void incidentsSplitByWhoHoldsTheAssetNow() {
    assertThat(
            counts(
                audit.countIncidentsByHolder(
                    ACME, AuditService.ENTITY_TYPE, INCIDENTS, HolderType.PERSON)))
        .containsOnly(entry(SAM, 1L));

    assertThat(counts(audit.countIncidentsByHolderType(ACME, AuditService.ENTITY_TYPE, INCIDENTS)))
        .containsOnly(
            entry(HolderType.PERSON.name(), 1L),
            entry(HolderType.LOCATION.name(), 1L),
            entry(HolderType.STOCKROOM.name(), 1L));
  }

  private static Asset asset(String type, String serial, String tag) {
    return new Asset(ACME, type, serial, tag);
  }

  private static Asset costing(Asset asset, long cents) {
    asset.setPurchaseCostCents(cents);
    return asset;
  }

  private static Asset held(Asset asset, long personId) {
    asset.assignTo(HolderType.PERSON, personId);
    return asset;
  }

  private static Asset supersedes(Asset asset, long replacedId) {
    asset.setSupersedesAssetId(replacedId);
    return asset;
  }

  private static AuditEvent event(Long clientId, String action, Long entityId) {
    return new AuditEvent(
        clientId, "tech@acme.example", action, AuditService.ENTITY_TYPE, entityId, action, null);
  }

  private static Map<String, Long> counts(List<CountBucket> rows) {
    return rows.stream().collect(Collectors.toMap(CountBucket::getBucket, CountBucket::getTotal));
  }

  private static Map.Entry<String, Long> entry(String bucket, long total) {
    return Map.entry(bucket, total);
  }

  private static Map.Entry<String, Long> entry(long bucket, long total) {
    return Map.entry(String.valueOf(bucket), total);
  }
}
