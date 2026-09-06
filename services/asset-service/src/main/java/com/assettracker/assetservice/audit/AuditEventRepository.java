package com.assettracker.assetservice.audit;

import com.assettracker.assetservice.entity.HolderType;
import com.assettracker.assetservice.repository.CountBucket;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

  @Query(
      """
      select e from AuditEvent e
      where e.clientId = :clientId
        and (:entityType is null or e.entityType = :entityType)
        and (:entityId is null or e.entityId = :entityId)
        and (:actor is null or e.actor = :actor)
        and (:action is null or e.action = :action)
        and (:since is null or e.at >= :since)
      order by e.at desc, e.id desc
      """)
  List<AuditEvent> search(
      @Param("clientId") Long clientId,
      @Param("entityType") String entityType,
      @Param("entityId") Long entityId,
      @Param("actor") String actor,
      @Param("action") String action,
      @Param("since") Instant since);

  // --- reports rollups ------------------------------------------------------

  /** How many times each thing happened, for the lifecycle table. */
  @Query(
      """
      select e.action as bucket, count(e) as total from AuditEvent e
      where e.clientId = :clientId and e.entityType = :entityType
      group by e.action
      """)
  List<CountBucket> countByAction(
      @Param("clientId") Long clientId, @Param("entityType") String entityType);

  /**
   * Break and loss events grouped by the type of the asset they touched.
   *
   * <p>An audit row records an entity id, not a foreign key - the trail outlives what it describes
   * - so this is an ad-hoc join. It also means an event whose asset has since been deleted drops
   * out, which is the same thing the console did when it looked the asset up in a map.
   */
  @Query(
      """
      select a.type as bucket, count(e) as total
      from AuditEvent e join Asset a on a.id = e.entityId and a.clientId = e.clientId
      where e.clientId = :clientId and e.entityType = :entityType and e.action in :actions
      group by a.type
      """)
  List<CountBucket> countIncidentsByAssetType(
      @Param("clientId") Long clientId,
      @Param("entityType") String entityType,
      @Param("actions") Collection<String> actions);

  /** The same events grouped by who currently holds the asset. */
  @Query(
      """
      select cast(a.holderId as string) as bucket, count(e) as total
      from AuditEvent e join Asset a on a.id = e.entityId and a.clientId = e.clientId
      where e.clientId = :clientId
        and e.entityType = :entityType
        and e.action in :actions
        and a.holderType = :holderType
        and a.holderId is not null
      group by a.holderId
      """)
  List<CountBucket> countIncidentsByHolder(
      @Param("clientId") Long clientId,
      @Param("entityType") String entityType,
      @Param("actions") Collection<String> actions,
      @Param("holderType") HolderType holderType);

  /** And by where the asset sits, so "on a desk" and "in the stockroom" are their own rows. */
  @Query(
      """
      select cast(a.holderType as string) as bucket, count(e) as total
      from AuditEvent e join Asset a on a.id = e.entityId and a.clientId = e.clientId
      where e.clientId = :clientId and e.entityType = :entityType and e.action in :actions
      group by a.holderType
      """)
  List<CountBucket> countIncidentsByHolderType(
      @Param("clientId") Long clientId,
      @Param("entityType") String entityType,
      @Param("actions") Collection<String> actions);
}
