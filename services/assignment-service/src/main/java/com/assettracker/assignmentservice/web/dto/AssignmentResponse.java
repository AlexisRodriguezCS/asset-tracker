package com.assettracker.assignmentservice.web.dto;

import com.assettracker.assignmentservice.entity.Assignment;
import java.time.Instant;

/** Response view of an assignment (custody episode). */
public record AssignmentResponse(
    Long id,
    Long clientId,
    Long assetId,
    String holderType,
    Long holderId,
    String checkedOutBy,
    Instant checkedOutAt,
    Instant returnedAt,
    String returnedBy,
    boolean open,
    String note) {

  public static AssignmentResponse from(Assignment a) {
    return new AssignmentResponse(
        a.getId(),
        a.getClientId(),
        a.getAssetId(),
        a.getHolderType().name(),
        a.getHolderId(),
        a.getCheckedOutBy(),
        a.getCheckedOutAt(),
        a.getReturnedAt(),
        a.getReturnedBy(),
        a.isOpen(),
        a.getNote());
  }
}
