package com.assettracker.assetservice.entity;

/**
 * The broad kind of thing being tracked. Coarse and fixed; a per-client, free-text {@code category}
 * on the asset itself covers finer, tenant-specific grouping.
 */
public enum AssetType {
  LAPTOP,
  TABLET,
  PHONE,
  MONITOR,
  DOCK,
  CHARGER,
  CABLE,
  HOTSPOT,
  PERIPHERAL,
  OTHER
}
