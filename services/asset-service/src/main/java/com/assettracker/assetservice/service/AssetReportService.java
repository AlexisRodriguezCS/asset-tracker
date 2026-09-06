package com.assettracker.assetservice.service;

import com.assettracker.assetservice.audit.AuditEventRepository;
import com.assettracker.assetservice.audit.AuditService;
import com.assettracker.assetservice.entity.AssetStatus;
import com.assettracker.assetservice.entity.HolderType;
import com.assettracker.assetservice.repository.AssetRepository;
import com.assettracker.assetservice.repository.CountBucket;
import com.assettracker.assetservice.web.CallerContext;
import com.assettracker.assetservice.web.TenantContext;
import com.assettracker.assetservice.web.dto.AssetReport;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reports page, answered in the database.
 *
 * <p>It is the one view that spans both halves of this service - the catalog and its audit trail -
 * which is why it is its own service rather than another method on {@link AssetSummaryService}. The
 * page used to fetch every asset <em>and</em> every audit row and group them in the render; both
 * are unbounded, so a tenant ten times the size cost ten times the transfer to draw the same dozen
 * bar charts.
 *
 * <p>Staff only. Every number here is a tenant-wide total, which is exactly what an ordinary
 * employee must not see - and unlike the asset list there is no sensible scoped version of "fleet
 * value", so the answer is a refusal rather than a filtered one.
 */
@Service
public class AssetReportService {

  /** Assets that still count towards what the fleet is worth - written-off units do not. */
  private static final Set<AssetStatus> IN_ESTATE =
      EnumSet.of(AssetStatus.IN_STOCK, AssetStatus.ASSIGNED, AssetStatus.IN_REPAIR);

  /** The audit actions that mean a unit was damaged or never came back. */
  private static final Collection<String> INCIDENTS =
      List.of("ASSET_STATUS_BROKEN", "ASSET_STATUS_LOST");

  private final AssetRepository assets;
  private final AuditEventRepository audit;

  public AssetReportService(AssetRepository assets, AuditEventRepository audit) {
    this.assets = assets;
    this.audit = audit;
  }

  /**
   * Every rollup the reports page draws.
   *
   * @param clientId the tenant to report on
   * @param soonDays how far ahead "expiring soon" looks
   * @param topSlots how many of the most-replaced tag slots to name
   */
  @Transactional(readOnly = true)
  public AssetReport report(Long clientId, int soonDays, int topSlots) {
    TenantContext.requireAllowed(clientId);
    CallerContext.requireStaff();

    LocalDate today = LocalDate.now();
    long expired = assets.countWarrantyEndingBefore(clientId, today, AssetStatus.IN_SERVICE);
    long throughSoon =
        assets.countWarrantyEndingBefore(
            clientId, today.plusDays(soonDays), AssetStatus.IN_SERVICE);
    long covered = assets.countWarrantyKnown(clientId, AssetStatus.IN_SERVICE);

    return new AssetReport(
        assets.countByClientId(clientId),
        counts(assets.countByType(clientId)),
        counts(assets.countByStatus(clientId)),
        counts(assets.countByCondition(clientId)),
        assets.countByClientIdAndConditionIsNull(clientId),
        expired,
        throughSoon - expired,
        covered - throughSoon,
        assets.sumPurchaseCost(clientId, IN_ESTATE),
        assets.countByClientIdAndSupersedesAssetIdNotNull(clientId),
        slots(clientId, topSlots),
        holdings(
            assets.countByHolderType(clientId),
            counts(assets.countByHolder(clientId, HolderType.PERSON))),
        counts(audit.countByAction(clientId, AuditService.ENTITY_TYPE)),
        incidents(clientId));
  }

  private List<AssetReport.Slot> slots(Long clientId, int topSlots) {
    return assets.countReplacedSlots(clientId, PageRequest.of(0, Math.max(1, topSlots))).stream()
        .map(s -> new AssetReport.Slot(s.getAssetTag(), s.getType(), s.getTotal()))
        .toList();
  }

  private AssetReport.Incidents incidents(Long clientId) {
    Map<String, Long> byType =
        counts(audit.countIncidentsByAssetType(clientId, AuditService.ENTITY_TYPE, INCIDENTS));
    return new AssetReport.Incidents(
        byType.values().stream().mapToLong(Long::longValue).sum(),
        byType,
        holdings(
            audit.countIncidentsByHolderType(clientId, AuditService.ENTITY_TYPE, INCIDENTS),
            counts(
                audit.countIncidentsByHolder(
                    clientId, AuditService.ENTITY_TYPE, INCIDENTS, HolderType.PERSON))));
  }

  /**
   * Splits a group-by on holder type into the two buckets the page names outright, and carries the
   * per-person counts through untouched - the department behind each id is people-service's to
   * know, so the caller finishes the join.
   */
  private static AssetReport.Holdings holdings(
      List<CountBucket> byHolderType, Map<String, Long> byPerson) {
    Map<String, Long> holders = counts(byHolderType);
    return new AssetReport.Holdings(
        holders.getOrDefault(HolderType.LOCATION.name(), 0L),
        holders.getOrDefault(HolderType.STOCKROOM.name(), 0L),
        byPerson);
  }

  private static Map<String, Long> counts(List<CountBucket> rows) {
    Map<String, Long> counts = new LinkedHashMap<>();
    for (CountBucket row : rows) {
      if (row.getBucket() != null) {
        counts.put(row.getBucket(), row.getTotal());
      }
    }
    return counts;
  }
}
