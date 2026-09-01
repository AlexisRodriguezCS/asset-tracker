package com.assettracker.assetservice.entity;

/** Physical state of an asset, independent of where it is in its service life. */
public enum AssetCondition {
  NEW,
  GOOD,
  FAIR,
  POOR,
  DAMAGED
}
