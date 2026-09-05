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

  /** Grouped counts for the summary strips - one row per bucket, not one per asset. */
  interface Bucket {
    String getBucket();

    long getTotal();
  }

  @Query(
      """
      select cast(a.status as string) as bucket, count(a) as total from Asset a
      where a.clientId = :clientId group by a.status
      """)
  List<Bucket> countByStatus(@Param("clientId") Long clientId);

  @Query(
      """
      select a.type as bucket, count(a) as total from Asset a
      where a.clientId = :clientId group by a.type
      """)
  List<Bucket> countByType(@Param("clientId") Long clientId);

  @Query(
      """
      select cast(a.condition as string) as bucket, count(a) as total from Asset a
      where a.clientId = :clientId group by a.condition
      """)
  List<Bucket> countByCondition(@Param("clientId") Long clientId);

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
}
