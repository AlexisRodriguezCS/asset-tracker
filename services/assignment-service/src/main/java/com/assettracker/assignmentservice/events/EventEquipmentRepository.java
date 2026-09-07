package com.assettracker.assignmentservice.events;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventEquipmentRepository extends JpaRepository<EventEquipment, Long> {

  List<EventEquipment> findByClientIdOrderByItemType(Long clientId);

  Optional<EventEquipment> findByClientIdAndItemTypeIgnoreCase(Long clientId, String itemType);

  /** One row per item type already spoken for on a date. */
  interface Committed {
    String getItemType();

    long getTotal();
  }

  /**
   * How much of each item type is already committed for one day.
   *
   * <p>A request holds stock from the moment it is raised, not from approval: two people asking for
   * the same two TVs on the same day is the collision this exists to prevent, and it happens at
   * request time. It stops holding when the request is denied, or closed once the gear is back.
   */
  @Query(
      """
      select l.itemType as itemType, sum(l.quantity) as total
      from EventRequest r join r.lines l
      where r.clientId = :clientId
        and r.eventDate = :date
        and r.status in :holding
      group by l.itemType
      """)
  List<Committed> committedOn(
      @Param("clientId") Long clientId,
      @Param("date") LocalDate date,
      @Param("holding") List<EventRequestStatus> holding);
}
