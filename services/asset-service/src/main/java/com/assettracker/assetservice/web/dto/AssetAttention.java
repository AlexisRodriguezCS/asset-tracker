package com.assettracker.assetservice.web.dto;

import java.util.List;

/**
 * The "needs attention" buckets, in one call: how many are in each, plus enough of each to preview.
 *
 * <p>Purpose-built rather than composed from the generic list endpoint. The dashboard wants four
 * counts and four short samples; assembling that from five generic calls means five round trips and
 * a status filter that cannot express "IN_REPAIR or BROKEN". One endpoint that answers the question
 * the page actually asks is both faster and easier to read.
 */
public record AssetAttention(List<Bucket> buckets) {

  /**
   * @param key stable identifier the console maps to a title and a link
   * @param total how many assets are in this bucket
   * @param sample the first few, for the preview list
   */
  public record Bucket(String key, long total, List<AssetResponse> sample) {}

  public static final String REPAIR = "repair";
  public static final String OUT_OF_WARRANTY = "outOfWarranty";
  public static final String EXPIRING_SOON = "warrantyExpiringSoon";
  public static final String PENDING_RECYCLE = "pendingRecycle";
}
