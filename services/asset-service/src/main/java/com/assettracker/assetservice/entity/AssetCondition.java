package com.assettracker.assetservice.entity;

/**
 * Physical grade of an asset, largely independent of where it is in its service life. The two are
 * only tied at the extremes: a {@code BROKEN} asset is always {@code DAMAGED}, and an asset in the
 * {@code IN_STOCK} pool is never {@code DAMAGED} (see {@code Asset#alignConditionToStatus}).
 */
public enum AssetCondition {
  NEW,
  GOOD,
  FAIR,
  POOR,
  DAMAGED
}
