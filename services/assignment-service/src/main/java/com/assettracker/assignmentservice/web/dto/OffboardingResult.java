package com.assettracker.assignmentservice.web.dto;

import java.util.ArrayList;
import java.util.List;

/** Summary of an offboarding sweep: which of the person's assets came back and which did not. */
public record OffboardingResult(Long personId, List<Long> returned, List<Long> failed) {

  public OffboardingResult(Long personId) {
    this(personId, new ArrayList<>(), new ArrayList<>());
  }
}
