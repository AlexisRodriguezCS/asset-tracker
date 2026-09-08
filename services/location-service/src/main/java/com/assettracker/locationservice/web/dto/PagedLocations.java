package com.assettracker.locationservice.web.dto;

import com.assettracker.locationservice.entity.Location;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * One page of locations, in the shape asset-service and people-service already use.
 *
 * <p>The desk map itself still reads the unpaged list on purpose: it groups by building and floor,
 * and a page boundary through the middle of a floor is not a map. This exists for the flat lists
 * and for any caller that should not be handed a whole estate.
 */
public record PagedLocations(
    List<LocationResponse> items, long total, int page, int size, int totalPages) {

  public static PagedLocations from(Page<Location> page) {
    return new PagedLocations(
        page.getContent().stream().map(LocationResponse::from).toList(),
        page.getTotalElements(),
        page.getNumber(),
        page.getSize(),
        page.getTotalPages());
  }
}
