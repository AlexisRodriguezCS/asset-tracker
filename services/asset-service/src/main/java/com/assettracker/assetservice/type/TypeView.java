package com.assettracker.assetservice.type;

import com.assettracker.assetservice.entity.AssetType;

/** Response view of a managed type. */
public record TypeView(Long id, Long clientId, String name) {

  public static TypeView from(AssetType t) {
    return new TypeView(t.getId(), t.getClientId(), t.getName());
  }
}
