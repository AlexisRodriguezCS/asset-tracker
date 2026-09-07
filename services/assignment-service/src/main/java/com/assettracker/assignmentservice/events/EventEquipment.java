package com.assettracker.assignmentservice.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * How many of one thing a client owns for events - "Acme has 2 TVs".
 *
 * <p>Deliberately a pool with a count rather than a view over the asset catalog. A request is
 * raised for "2 TVs" before anyone knows which two, and the things clients lend at events are not
 * always tracked as individual serialised units - a box of cables is one line item, not twelve
 * asset rows. Fulfilment is where the abstraction ends: a tech names real assets and checks them
 * out through the normal custody path.
 *
 * <p>The trade-off is that this count and the catalog can drift. Keeping them in step is a
 * reconciliation job, not a foreign key, and is left out until someone wants it.
 */
@Entity
@Table(name = "event_equipment")
public class EventEquipment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long clientId;

  @Column(nullable = false, length = 80)
  private String itemType;

  @Column(nullable = false)
  private int quantity;

  @Column(nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected EventEquipment() {}

  public EventEquipment(Long clientId, String itemType, int quantity) {
    this.clientId = clientId;
    this.itemType = itemType;
    this.quantity = quantity;
  }

  /**
   * Changes how many the client owns.
   *
   * <p>Lowering it below what is already committed for a date is allowed: gear gets sold, broken or
   * written off, and the requests already agreed for next Tuesday do not evaporate because of it.
   * Availability simply floors at zero, and nothing new can be booked until those close.
   */
  public void setQuantity(int quantity) {
    if (quantity < 0) {
      throw new IllegalArgumentException("quantity cannot be negative");
    }
    this.quantity = quantity;
  }

  public Long getId() {
    return id;
  }

  public Long getClientId() {
    return clientId;
  }

  public String getItemType() {
    return itemType;
  }

  public int getQuantity() {
    return quantity;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
