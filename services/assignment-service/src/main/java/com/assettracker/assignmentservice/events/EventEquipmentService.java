package com.assettracker.assignmentservice.events;

import com.assettracker.assignmentservice.audit.AuditService;
import com.assettracker.assignmentservice.events.EventViews.Availability;
import com.assettracker.assignmentservice.events.EventViews.EquipmentRequest;
import com.assettracker.assignmentservice.events.EventViews.LineRequest;
import com.assettracker.assignmentservice.web.CallerContext;
import com.assettracker.assignmentservice.web.TenantContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The pool of gear a client lends at events, and what is left of it on a given day.
 *
 * <p>Availability is a subtraction, not a stored counter: what the client owns, minus what is
 * already spoken for that day. Nothing decrements on request and nothing has to be put back on
 * denial - the sum is recomputed from the requests themselves, so it cannot drift out of step with
 * them the way a running total can.
 */
@Service
public class EventEquipmentService {

  private final EventEquipmentRepository equipment;
  private final AuditService audit;

  public EventEquipmentService(EventEquipmentRepository equipment, AuditService audit) {
    this.equipment = equipment;
    this.audit = audit;
  }

  /**
   * What the client owns and what is left of it on {@code date}.
   *
   * <p>Readable by anyone signed in, employees included - it is the sign-out form's menu, and an
   * employee has to be able to see that both TVs are gone before they ask for one.
   */
  @Transactional(readOnly = true)
  public List<Availability> availability(Long clientId, LocalDate date) {
    TenantContext.requireAllowed(clientId);
    Map<String, Long> committed = committed(clientId, date);
    return equipment.findByClientIdOrderByItemType(clientId).stream()
        .map(
            item -> {
              long taken = committed.getOrDefault(key(item.getItemType()), 0L);
              return new Availability(
                  item.getId(),
                  item.getItemType(),
                  item.getQuantity(),
                  taken,
                  Math.max(0, item.getQuantity() - taken));
            })
        .toList();
  }

  /** Adds an item type to the client's pool, or changes how many of it they own. */
  @Transactional
  public EventEquipment save(EquipmentRequest body, String actor) {
    TenantContext.requireAllowed(body.clientId());
    CallerContext.requireAssetOperator();

    String itemType = body.itemType().trim();
    EventEquipment item =
        equipment
            .findByClientIdAndItemTypeIgnoreCase(body.clientId(), itemType)
            .orElseGet(() -> new EventEquipment(body.clientId(), itemType, 0));
    int before = item.getQuantity();
    item.setQuantity(body.quantity());

    EventEquipment saved = equipment.save(item);
    audit.record(
        saved.getClientId(),
        actor,
        "EVENT_EQUIPMENT_SET",
        saved.getId(),
        actor
            + " set "
            + saved.getItemType()
            + " to "
            + saved.getQuantity()
            + " (was "
            + before
            + ")",
        null);
    return saved;
  }

  /** Removes an item type from the pool entirely. */
  @Transactional
  public void delete(Long id, String actor) {
    EventEquipment item =
        equipment.findById(id).orElseThrow(() -> new EventEquipmentNotFoundException(id));
    TenantContext.requireAllowed(item.getClientId());
    CallerContext.requireAssetOperator();
    equipment.delete(item);
    audit.record(
        item.getClientId(),
        actor,
        "EVENT_EQUIPMENT_REMOVED",
        id,
        actor + " removed " + item.getItemType() + " from the event pool",
        null);
  }

  /**
   * Checks the lines of a request against what is free that day, and refuses the whole request if
   * any line does not fit.
   *
   * <p>All-or-nothing on purpose: half a kit is not a smaller version of the booking, it is a
   * different one, and the requester is better off being told than turning up with one TV.
   */
  @Transactional(readOnly = true)
  public void requireAvailable(Long clientId, LocalDate date, List<LineRequest> lines) {
    Map<String, Integer> owned = new HashMap<>();
    for (EventEquipment item : equipment.findByClientIdOrderByItemType(clientId)) {
      owned.put(key(item.getItemType()), item.getQuantity());
    }
    Map<String, Long> committed = committed(clientId, date);
    List<String> problems = new ArrayList<>();

    // lines are summed first: two "1 x TV" lines on one request are two TVs, not one
    Map<String, Integer> wanted = new HashMap<>();
    Map<String, String> labels = new HashMap<>();
    for (LineRequest line : lines) {
      String name = line.itemType().trim();
      wanted.merge(key(name), line.quantity(), Integer::sum);
      labels.putIfAbsent(key(name), name);
    }

    for (Map.Entry<String, Integer> ask : wanted.entrySet()) {
      String label = labels.get(ask.getKey());
      Integer have = owned.get(ask.getKey());
      if (have == null) {
        problems.add(label + " is not something this client lends at events");
        continue;
      }
      long free = Math.max(0, have - committed.getOrDefault(ask.getKey(), 0L));
      if (ask.getValue() > free) {
        problems.add(
            "asked for "
                + ask.getValue()
                + " x "
                + label
                + " but only "
                + free
                + " of "
                + have
                + " free on "
                + date);
      }
    }

    if (!problems.isEmpty()) {
      throw new EventEquipmentUnavailableException(String.join("; ", problems));
    }
  }

  private Map<String, Long> committed(Long clientId, LocalDate date) {
    Map<String, Long> totals = new HashMap<>();
    if (date == null) {
      return totals;
    }
    for (EventEquipmentRepository.Committed row :
        equipment.committedOn(clientId, date, EventRequestStatus.holdingStock())) {
      totals.merge(key(row.getItemType()), row.getTotal(), Long::sum);
    }
    return totals;
  }

  /** Item types are matched case-insensitively, so "TV" and "Tv" are the same pool. */
  private static String key(String itemType) {
    return itemType.trim().toLowerCase(Locale.ROOT);
  }
}
