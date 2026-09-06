package com.assettracker.assetservice.repository;

import com.assettracker.assetservice.entity.Asset;
import com.assettracker.assetservice.entity.AssetStatus;
import com.assettracker.assetservice.entity.HolderType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, Long> {

  /** Newest first, so callers can take the current unit or walk the tag's history. */
  List<Asset> findByClientIdAndAssetTagOrderByIdDesc(Long clientId, String assetTag);

  Optional<Asset> findFirstByAssetTagAndStatusInOrderByIdDesc(
      String assetTag, Collection<AssetStatus> statuses);

  Optional<Asset> findFirstByAssetTagOrderByIdDesc(String assetTag);

  /** Every asset of a client currently on a type name - the "what breaks if I delete it" list. */
  List<Asset> findByClientIdAndType(Long clientId, String type);

  /** The import upsert key: a client's units on one tag + type (usually 0 or 1). */
  List<Asset> findByClientIdAndAssetTagAndType(Long clientId, String assetTag, String type);

  /** Every asset of a client - the import loads these once and indexes them in memory. */
  List<Asset> findByClientId(Long clientId);

  /**
   * True when a client already has an in-service asset of this type on the tag. A tag may carry one
   * active asset per type (a laptop plus its bundled charger and cable), and a retired / lost /
   * recycled unit frees its slot for a replacement.
   */
  @Query(
      """
      select count(a) > 0 from Asset a
      where a.clientId = :clientId
        and a.assetTag = :assetTag
        and a.type = :type
        and a.status in :statuses
      """)
  boolean existsActiveWithTag(
      @Param("clientId") Long clientId,
      @Param("assetTag") String assetTag,
      @Param("type") String type,
      @Param("statuses") Collection<AssetStatus> statuses);

  @Query(
      """
      select cast(a.status as string) as bucket, count(a) as total from Asset a
      where a.clientId = :clientId group by a.status
      """)
  List<CountBucket> countByStatus(@Param("clientId") Long clientId);

  @Query(
      """
      select a.type as bucket, count(a) as total from Asset a
      where a.clientId = :clientId group by a.type
      """)
  List<CountBucket> countByType(@Param("clientId") Long clientId);

  @Query(
      """
      select cast(a.condition as string) as bucket, count(a) as total from Asset a
      where a.clientId = :clientId group by a.condition
      """)
  List<CountBucket> countByCondition(@Param("clientId") Long clientId);

  /**
   * In-service assets whose warranty ends before {@code before}. Passing today gives the
   * already-expired count; passing today+N gives expired plus expiring within N, so "expiring soon"
   * is the difference between the two.
   */
  @Query(
      """
      select count(a) from Asset a
      where a.clientId = :clientId
        and a.warrantyEndsOn is not null
        and a.warrantyEndsOn < :before
        and a.status in :inService
      """)
  long countWarrantyEndingBefore(
      @Param("clientId") Long clientId,
      @Param("before") java.time.LocalDate before,
      @Param("inService") Collection<AssetStatus> inService);

  long countByClientId(Long clientId);

  /**
   * In-service assets whose warranty ends inside a window. {@code from} null means "however long
   * ago", so (null, today) is everything already expired and (today, today+N) is expiring soon.
   * Paged so a caller can take a handful for a preview without loading the rest.
   */
  @Query(
      """
      select a from Asset a
      where a.clientId = :clientId
        and a.warrantyEndsOn is not null
        and a.status in :inService
        and (:from is null or a.warrantyEndsOn >= :from)
        and a.warrantyEndsOn < :to
      order by a.warrantyEndsOn
      """)
  Page<Asset> findByWarrantyWindow(
      @Param("clientId") Long clientId,
      @Param("from") java.time.LocalDate from,
      @Param("to") java.time.LocalDate to,
      @Param("inService") Collection<AssetStatus> inService,
      Pageable pageable);

  /** A client's assets in any of the given statuses - the "needs attention" buckets. */
  Page<Asset> findByClientIdAndStatusIn(
      Long clientId, Collection<AssetStatus> statuses, Pageable pageable);

  List<Asset> findByHolderTypeAndHolderId(HolderType holderType, Long holderId);

  /**
   * The one list query behind every catalog view. Any of {@code type} / {@code status} / {@code
   * holderType} / {@code holderId} / {@code assetTag} may be null to widen the filter.
   */
  @Query(
      """
      select a from Asset a
      where a.clientId = :clientId
        and (:type is null or a.type = :type)
        and (:status is null or a.status = :status)
        and (:holderType is null or a.holderType = :holderType)
        and (:holderId is null or a.holderId = :holderId)
        and (:assetTag is null or a.assetTag = :assetTag)
      order by a.type, a.assetTag
      """)
  List<Asset> search(
      @Param("clientId") Long clientId,
      @Param("type") String type,
      @Param("status") AssetStatus status,
      @Param("holderType") HolderType holderType,
      @Param("holderId") Long holderId,
      @Param("assetTag") String assetTag);

  /**
   * The same filter, one page at a time. The catalog list renders a row per asset, so an unbounded
   * query there costs roughly 2kB of HTML per asset - fine for a demo tenant, not for a real one.
   * Aggregating callers (dashboard, reports, type counts) still use the unpaged twin above.
   */
  @Query(
      """
      select a from Asset a
      where a.clientId = :clientId
        and (:type is null or a.type = :type)
        and (:status is null or a.status = :status)
        and (:holderType is null or a.holderType = :holderType)
        and (:holderId is null or a.holderId = :holderId)
        and (:assetTag is null or a.assetTag = :assetTag)
      """)
  Page<Asset> searchPage(
      @Param("clientId") Long clientId,
      @Param("type") String type,
      @Param("status") AssetStatus status,
      @Param("holderType") HolderType holderType,
      @Param("holderId") Long holderId,
      @Param("assetTag") String assetTag,
      Pageable pageable);

  // --- reports rollups ------------------------------------------------------
  //
  // The reports page used to pull every asset and group it in the render. These
  // answer the same questions in the database; each returns one row per bucket.

  /** Purchase cost of the assets still in the estate - written off units do not count. */
  @Query(
      """
      select coalesce(sum(a.purchaseCostCents), 0) from Asset a
      where a.clientId = :clientId and a.status in :statuses
      """)
  long sumPurchaseCost(
      @Param("clientId") Long clientId, @Param("statuses") Collection<AssetStatus> statuses);

  /** In-service assets that carry a warranty date at all, so "in warranty" is a subtraction. */
  @Query(
      """
      select count(a) from Asset a
      where a.clientId = :clientId
        and a.warrantyEndsOn is not null
        and a.status in :inService
      """)
  long countWarrantyKnown(
      @Param("clientId") Long clientId, @Param("inService") Collection<AssetStatus> inService);

  /** Assets with no condition recorded - a group-by on condition cannot count nulls as a bucket. */
  long countByClientIdAndConditionIsNull(Long clientId);

  /** Units created by retire-and-replace to take over from another. */
  long countByClientIdAndSupersedesAssetIdNotNull(Long clientId);

  @Query(
      """
      select cast(a.holderType as string) as bucket, count(a) as total from Asset a
      where a.clientId = :clientId group by a.holderType
      """)
  List<CountBucket> countByHolderType(@Param("clientId") Long clientId);

  /**
   * Assets per holding person. Bounded by headcount rather than catalog size, which is what makes
   * it safe to return whole: the department each id belongs to lives in people-service, so the
   * caller finishes this rollup by joining the people list it already has.
   */
  @Query(
      """
      select cast(a.holderId as string) as bucket, count(a) as total from Asset a
      where a.clientId = :clientId and a.holderType = :holderType and a.holderId is not null
      group by a.holderId
      """)
  List<CountBucket> countByHolder(
      @Param("clientId") Long clientId, @Param("holderType") HolderType holderType);

  /** A tag + type slot and the units it has burned through, heaviest first. */
  interface Slot {
    String getAssetTag();

    String getType();

    long getTotal();
  }

  @Query(
      """
      select a.assetTag as assetTag, a.type as type, count(a) as total from Asset a
      where a.clientId = :clientId and a.supersedesAssetId is not null
      group by a.assetTag, a.type
      order by count(a) desc, a.assetTag
      """)
  List<Slot> countReplacedSlots(@Param("clientId") Long clientId, Pageable pageable);
}
