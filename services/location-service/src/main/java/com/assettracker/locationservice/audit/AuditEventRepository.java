package com.assettracker.locationservice.audit;

import java.time.Instant;
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
}
