package com.assettracker.assetservice.web.dto;

import java.util.Map;

/**
 * Counts for the summary strips and rollups, aggregated in the database.
 *
 * <p>The console used to fetch every asset and count in JavaScript, which cost a 416kB payload per
 * render on four pages. Counting is what a database is for.
 *
 * @param total every asset visible to the caller
 * @param byStatus asset count per {@code AssetStatus}
 * @param byType asset count per type name
 * @param byCondition asset count per condition
 * @param outOfWarranty in-service assets whose warranty has already ended
 * @param warrantyExpiringSoon in-service assets whose warranty ends within the configured window
 */
public record AssetStats(
    long total,
    Map<String, Long> byStatus,
    Map<String, Long> byType,
    Map<String, Long> byCondition,
    long outOfWarranty,
    long warrantyExpiringSoon) {}
