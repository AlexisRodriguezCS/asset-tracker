package com.assettracker.assignmentservice.web.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Summary of an offboarding sweep.
 *
 * <p>Three buckets, not two, because "it did not work" hides two opposite situations. Collecting an
 * asset is two steps - asset-service puts it back in stock, then this service closes the assignment
 * - and only the first one moves the laptop.
 *
 * <ul>
 *   <li>{@code returned} - back in stock and the assignment is closed.
 *   <li>{@code failed} - the return itself did not happen. The person still has it; go and ask.
 *   <li>{@code unrecorded} - the asset <em>is</em> back in stock, but its assignment did not close.
 *       Nobody needs to chase the employee; someone needs to fix the record. Reporting these as
 *       {@code failed} is what sent people after laptops already sitting on the shelf.
 * </ul>
 */
public record OffboardingResult(
    Long personId, List<Long> returned, List<Long> failed, List<Long> unrecorded) {

  public OffboardingResult(Long personId) {
    this(personId, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
  }
}
