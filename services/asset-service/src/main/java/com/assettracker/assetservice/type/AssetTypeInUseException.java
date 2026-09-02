package com.assettracker.assetservice.type;

import java.util.List;

/**
 * The type still has assets on it and no {@code reassignTo} was given. Carries the affected assets
 * so the console can show "deleting Hotspot would affect X, Y, Z". Maps to HTTP 409.
 */
public class AssetTypeInUseException extends RuntimeException {

  private final transient List<LinkedAsset> assets;

  public AssetTypeInUseException(String name, List<LinkedAsset> assets) {
    super("type '" + name + "' is still used by " + assets.size() + " asset(s)");
    this.assets = assets;
  }

  public List<LinkedAsset> getAssets() {
    return assets;
  }

  /** A compact reference to an asset that would be affected by deleting a type. */
  public record LinkedAsset(Long id, String assetTag, String make, String model, String status) {}
}
