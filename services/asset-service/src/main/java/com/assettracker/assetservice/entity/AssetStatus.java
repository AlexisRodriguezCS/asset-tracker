package com.assettracker.assetservice.entity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Where an asset sits in its service life. {@code IN_STOCK} / {@code ASSIGNED} are the everyday
 * states; the rest are exceptions. {@code BROKEN} is pulled from use pending triage, {@code
 * IN_REPAIR} is away at a vendor, {@code PENDING_RECYCLE} is staged for disposal, {@code RECYCLED}
 * has physically left, {@code RETIRED} is end-of-service but still on hand, {@code LOST} is
 * unaccounted for.
 */
public enum AssetStatus {
  IN_STOCK,
  ASSIGNED,
  IN_REPAIR,
  BROKEN,
  PENDING_RECYCLE,
  RECYCLED,
  RETIRED,
  LOST;

  /**
   * Statuses in which an asset still lays claim to its tag. An asset in any other status has left
   * active service, so a replacement may be created with the same tag (see {@code AssetService}).
   */
  public static final Set<AssetStatus> ACTIVE =
      Collections.unmodifiableSet(EnumSet.of(IN_STOCK, ASSIGNED, IN_REPAIR));
}
