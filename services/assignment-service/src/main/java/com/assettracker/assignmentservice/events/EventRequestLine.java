package com.assettracker.assignmentservice.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One "what and how many" line on a request - "2 x TV". Deliberately a type plus a count rather
 * than specific assets: the requester knows they need two TVs, not which two. Concrete asset ids
 * are attached later, at fulfilment, by whoever pulls the gear.
 */
@Entity
@Table(name = "event_request_lines")
public class EventRequestLine {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "request_id", nullable = false)
  private Long requestId;

  /** An asset type name, matching asset-service's per-client type catalog (e.g. "Laptop", "TV"). */
  @Column(nullable = false, length = 80)
  private String itemType;

  @Column(nullable = false)
  private int quantity;

  @Column(length = 500)
  private String notes;

  /** Comma-separated asset ids handed out for this line; empty until fulfilment. */
  @Column(name = "fulfilled_asset_ids", length = 500)
  private String fulfilledAssetIds = "";

  protected EventRequestLine() {}

  public EventRequestLine(String itemType, int quantity, String notes) {
    this.itemType = itemType;
    this.quantity = quantity;
    this.notes = notes;
  }

  public Long getId() {
    return id;
  }

  public Long getRequestId() {
    return requestId;
  }

  void setRequestId(Long requestId) {
    this.requestId = requestId;
  }

  public String getItemType() {
    return itemType;
  }

  public int getQuantity() {
    return quantity;
  }

  public String getNotes() {
    return notes;
  }

  public String getFulfilledAssetIds() {
    return fulfilledAssetIds == null ? "" : fulfilledAssetIds;
  }

  public void setFulfilledAssetIds(String fulfilledAssetIds) {
    this.fulfilledAssetIds = fulfilledAssetIds;
  }
}
