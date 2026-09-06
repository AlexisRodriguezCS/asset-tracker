package com.assettracker.assetservice.web.dto;

import java.util.List;
import java.util.Map;

/**
 * Everything the reports page shows, aggregated in the database.
 *
 * <p>It is the last page that used to fetch the whole catalog - and the whole audit trail - to
 * group it in the render. Both are unbounded, so the cost grew with the tenant while the rendered
 * page stayed the same size.
 *
 * <p>Departments are the one rollup that cannot be finished here: an asset knows the id of the
 * person holding it, but the department lives in people-service. So this returns counts per holder
 * id - bounded by headcount, not by catalog size - and the console joins them against the people
 * list it already has.
 *
 * @param total every asset of the tenant
 * @param byType asset count per type name
 * @param byStatus asset count per status
 * @param byCondition asset count per condition, excluding unrated
 * @param unrated assets with no condition recorded
 * @param outOfWarranty in-service assets whose warranty has already ended
 * @param warrantyExpiringSoon in-service assets whose warranty ends within the requested window
 * @param inWarranty in-service assets covered beyond that window
 * @param fleetValueCents purchase cost of the assets still in the estate
 * @param replacements assets created by retire-and-replace to take over from another unit
 * @param topReplacedSlots the tag + type slots that have burned through the most units
 * @param holdings who holds the fleet, for the department rollup
 * @param lifecycle audit action to the number of times it happened
 * @param incidents break and loss events, and what they were
 */
public record AssetReport(
    long total,
    Map<String, Long> byType,
    Map<String, Long> byStatus,
    Map<String, Long> byCondition,
    long unrated,
    long outOfWarranty,
    long warrantyExpiringSoon,
    long inWarranty,
    long fleetValueCents,
    long replacements,
    List<Slot> topReplacedSlots,
    Holdings holdings,
    Map<String, Long> lifecycle,
    Incidents incidents) {

  /**
   * A tag + type slot and how many units it has carried.
   *
   * @param assetTag the tag, which identifies a slot rather than a unit
   * @param type the asset type on that tag
   * @param count units that have taken over the slot
   */
  public record Slot(String assetTag, String type, long count) {}

  /**
   * Where a client's assets are, split the way the reports page groups them.
   *
   * @param onDesk assets held by a location
   * @param unassigned assets on the shelf
   * @param byPerson holder person id to the number of assets they hold
   */
  public record Holdings(long onDesk, long unassigned, Map<String, Long> byPerson) {}

  /**
   * Break and loss, counted from the audit trail and attributed to the asset's current holder - the
   * same attribution the page made when it joined these in the browser.
   *
   * @param total break and loss events on assets that still exist
   * @param byType events per asset type
   * @param holdings events per current holder
   */
  public record Incidents(long total, Map<String, Long> byType, Holdings holdings) {}
}
