package com.assettracker.assetservice.service;

import com.assettracker.assetservice.entity.Asset;
import com.assettracker.assetservice.entity.AssetStatus;
import com.assettracker.assetservice.repository.AssetRepository;
import com.assettracker.assetservice.repository.CountBucket;
import com.assettracker.assetservice.web.CallerContext;
import com.assettracker.assetservice.web.TenantContext;
import com.assettracker.assetservice.web.dto.AssetAttention;
import com.assettracker.assetservice.web.dto.AssetResponse;
import com.assettracker.assetservice.web.dto.AssetStats;
import com.assettracker.assetservice.web.dto.TypeUsage;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only rollups over the catalog: the summary counts, the "needs attention" buckets, and
 * per-type usage.
 *
 * <p>Separate from {@link AssetService}, which owns creating assets and moving custody. These
 * methods only ever read, they answer questions the console asks rather than modelling the domain,
 * and keeping them here stops the custody service growing a reporting department. They apply the
 * same tenant and employee scoping, because a count is still a disclosure.
 */
@Service
public class AssetSummaryService {

  private final AssetRepository repository;
  private final AssetService assets;

  public AssetSummaryService(AssetRepository repository, AssetService assets) {
    this.repository = repository;
    this.assets = assets;
  }

  /**
   * Counts for the summary strips, aggregated in the database rather than by fetching every asset
   * and counting in the caller.
   *
   * <p>An ordinary employee is the exception: their gear is a handful of rows, and the grouped
   * queries would have to be re-written per-person to scope correctly. Counting their own small set
   * in memory is both simpler and cheaper than a scoped group-by.
   */
  @Transactional(readOnly = true)
  public AssetStats stats(Long clientId, int soonDays) {
    TenantContext.requireAllowed(clientId);
    if (CallerContext.isSelfServiceUser()) {
      return statsOf(assets.search(clientId, null, null, null, null, null), soonDays);
    }

    LocalDate today = LocalDate.now();
    long expired = repository.countWarrantyEndingBefore(clientId, today, AssetStatus.IN_SERVICE);
    long throughSoon =
        repository.countWarrantyEndingBefore(
            clientId, today.plusDays(soonDays), AssetStatus.IN_SERVICE);

    return new AssetStats(
        repository.countByClientId(clientId),
        toMap(repository.countByStatus(clientId)),
        toMap(repository.countByType(clientId)),
        toMap(repository.countByCondition(clientId)),
        expired,
        throughSoon - expired);
  }

  /**
   * The dashboard's four buckets - count plus a short preview each - in one query per bucket rather
   * than by loading the catalog and slicing it in the caller.
   *
   * <p>Employees do not get this: "what needs attention across the fleet" is a staff question, and
   * an employee's own gear is already visible to them on their asset list.
   */
  @Transactional(readOnly = true)
  public AssetAttention attention(Long clientId, int sampleSize, int soonDays) {
    TenantContext.requireAllowed(clientId);
    if (CallerContext.isSelfServiceUser()) {
      return new AssetAttention(List.of());
    }

    LocalDate today = LocalDate.now();
    Pageable preview = PageRequest.of(0, Math.max(1, sampleSize));

    return new AssetAttention(
        List.of(
            bucket(
                AssetAttention.REPAIR,
                repository.findByClientIdAndStatusIn(
                    clientId, EnumSet.of(AssetStatus.IN_REPAIR, AssetStatus.BROKEN), preview)),
            bucket(
                AssetAttention.OUT_OF_WARRANTY,
                repository.findByWarrantyWindow(
                    clientId, null, today, AssetStatus.IN_SERVICE, preview)),
            bucket(
                AssetAttention.EXPIRING_SOON,
                repository.findByWarrantyWindow(
                    clientId, today, today.plusDays(soonDays), AssetStatus.IN_SERVICE, preview)),
            bucket(
                AssetAttention.PENDING_RECYCLE,
                repository.findByClientIdAndStatusIn(
                    clientId, EnumSet.of(AssetStatus.PENDING_RECYCLE), preview))));
  }

  /**
   * Per-type usage for the type manager: a count and a few example assets each. One query per type,
   * where the page previously pulled the whole catalog and grouped it in the render.
   */
  @Transactional(readOnly = true)
  public List<TypeUsage> typeUsage(Long clientId, List<String> typeNames, int sampleSize) {
    TenantContext.requireAllowed(clientId);
    Pageable preview = PageRequest.of(0, Math.max(1, sampleSize));
    return typeNames.stream()
        .map(
            name -> {
              Page<Asset> page = assets.searchPage(clientId, name, null, null, null, null, preview);
              return new TypeUsage(
                  name,
                  page.getTotalElements(),
                  page.getContent().stream().map(AssetResponse::from).toList());
            })
        .toList();
  }

  private static AssetAttention.Bucket bucket(String key, Page<Asset> page) {
    return new AssetAttention.Bucket(
        key, page.getTotalElements(), page.getContent().stream().map(AssetResponse::from).toList());
  }

  private static Map<String, Long> toMap(List<CountBucket> rows) {
    Map<String, Long> counts = new LinkedHashMap<>();
    for (CountBucket row : rows) {
      if (row.getBucket() != null) {
        counts.put(row.getBucket(), row.getTotal());
      }
    }
    return counts;
  }

  /** The same shape computed over an already-loaded list, for the self-service case. */
  private static AssetStats statsOf(List<Asset> assets, int soonDays) {
    LocalDate today = LocalDate.now();
    LocalDate soon = today.plusDays(soonDays);
    Map<String, Long> byStatus = new LinkedHashMap<>();
    Map<String, Long> byType = new LinkedHashMap<>();
    Map<String, Long> byCondition = new LinkedHashMap<>();
    long expired = 0;
    long expiringSoon = 0;
    for (Asset a : assets) {
      byStatus.merge(a.getStatus().name(), 1L, Long::sum);
      byType.merge(a.getType(), 1L, Long::sum);
      if (a.getCondition() != null) {
        byCondition.merge(a.getCondition().name(), 1L, Long::sum);
      }
      LocalDate ends = a.getWarrantyEndsOn();
      if (ends != null && AssetStatus.IN_SERVICE.contains(a.getStatus())) {
        if (ends.isBefore(today)) {
          expired++;
        } else if (ends.isBefore(soon)) {
          expiringSoon++;
        }
      }
    }
    return new AssetStats(assets.size(), byStatus, byType, byCondition, expired, expiringSoon);
  }
}
