package com.assettracker.assignmentservice.events;

import com.assettracker.assignmentservice.events.EventViews.Availability;
import com.assettracker.assignmentservice.events.EventViews.EquipmentRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The client's event equipment pool: what they lend, how many, and what is left on a day.
 *
 * <p>Reading is open to anyone signed in - it is the sign-out form's menu. Changing the pool is an
 * operator job, gated in the service alongside every other write.
 */
@RestController
@RequestMapping("/assignments/event-equipment")
public class EventEquipmentController {

  private static final String ACTOR = "X-User-Id";

  private final EventEquipmentService service;

  public EventEquipmentController(EventEquipmentService service) {
    this.service = service;
  }

  /**
   * The menu. Pass {@code date} to get availability for that day; without it the committed and
   * available columns are just the raw pool, which is what the equipment editor wants.
   */
  @GetMapping
  public List<Availability> list(
      @RequestParam Long clientId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return service.availability(clientId, date);
  }

  @PostMapping
  public Availability save(
      @Valid @RequestBody EquipmentRequest body,
      @RequestHeader(value = ACTOR, defaultValue = "system") String actor) {
    EventEquipment saved = service.save(body, actor);
    return new Availability(
        saved.getId(), saved.getItemType(), saved.getQuantity(), 0L, saved.getQuantity());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable Long id, @RequestHeader(value = ACTOR, defaultValue = "system") String actor) {
    service.delete(id, actor);
    return ResponseEntity.noContent().build();
  }
}
