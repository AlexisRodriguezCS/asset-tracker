package com.assettracker.assetservice.entity;

/** Raised when trying to assign an asset that is already assigned. Maps to HTTP 409. */
public class AlreadyAssignedException extends RuntimeException {

  public AlreadyAssignedException(Long assetId, HolderType holderType, Long holderId) {
    super(
        "asset "
            + assetId
            + " is already assigned to "
            + holderType
            + (holderId == null ? "" : " " + holderId));
  }
}
