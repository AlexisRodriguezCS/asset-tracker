package com.assettracker.assetservice.repository;

import com.assettracker.assetservice.entity.Asset;
import com.assettracker.assetservice.entity.AssetStatus;
import com.assettracker.assetservice.entity.AssetType;
import com.assettracker.assetservice.entity.HolderType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, Long> {

  Optional<Asset> findByAssetTag(String assetTag);

  boolean existsByAssetTag(String assetTag);

  List<Asset> findByHolderTypeAndHolderId(HolderType holderType, Long holderId);

  /**
   * The one list query behind every catalog view. Any of {@code type} / {@code status} / {@code
   * holderType} / {@code holderId} may be null to widen the filter.
   */
  @Query(
      """
      select a from Asset a
      where a.clientId = :clientId
        and (:type is null or a.type = :type)
        and (:status is null or a.status = :status)
        and (:holderType is null or a.holderType = :holderType)
        and (:holderId is null or a.holderId = :holderId)
      order by a.type, a.assetTag
      """)
  List<Asset> search(
      @Param("clientId") Long clientId,
      @Param("type") AssetType type,
      @Param("status") AssetStatus status,
      @Param("holderType") HolderType holderType,
      @Param("holderId") Long holderId);
}
