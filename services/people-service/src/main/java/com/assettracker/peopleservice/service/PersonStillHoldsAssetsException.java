package com.assettracker.peopleservice.service;

import java.util.List;

/**
 * Thrown when a person is marked departed while assets are still assigned to them. The offboarding
 * sweep (assignment-service) has to collect them first.
 */
public class PersonStillHoldsAssetsException extends RuntimeException {

  private final transient List<Long> assetIds;

  public PersonStillHoldsAssetsException(Long personId, List<Long> assetIds) {
    super(
        "person "
            + personId
            + " still holds "
            + assetIds.size()
            + " asset(s); run offboarding first");
    this.assetIds = List.copyOf(assetIds);
  }

  public List<Long> getAssetIds() {
    return assetIds;
  }
}
