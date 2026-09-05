package com.assettracker.assignmentservice.events;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRequestRepository extends JpaRepository<EventRequest, Long> {

  List<EventRequest> findByClientIdOrderByEventDateDesc(Long clientId);

  List<EventRequest> findByClientIdAndStatusOrderByEventDateDesc(
      Long clientId, EventRequestStatus status);

  /** What one employee has asked for - the "my requests" list. */
  List<EventRequest> findByClientIdAndRequesterPersonIdOrderByEventDateDesc(
      Long clientId, Long requesterPersonId);
}
