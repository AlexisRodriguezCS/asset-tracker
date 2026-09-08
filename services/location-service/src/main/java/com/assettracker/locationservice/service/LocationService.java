package com.assettracker.locationservice.service;

import com.assettracker.locationservice.audit.AuditService;
import com.assettracker.locationservice.client.AssetClient;
import com.assettracker.locationservice.entity.Location;
import com.assettracker.locationservice.entity.LocationKind;
import com.assettracker.locationservice.repository.LocationRepository;
import com.assettracker.locationservice.web.CallerContext;
import com.assettracker.locationservice.web.TenantContext;
import com.assettracker.locationservice.web.dto.CreateLocationRequest;
import com.assettracker.locationservice.web.dto.UpdateLocationRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business operations for locations (sites / rooms / desks). */
@Service
public class LocationService {

  private final LocationRepository repository;
  private final AuditService audit;
  private final AssetClient assetClient;

  public LocationService(
      LocationRepository repository, AuditService audit, AssetClient assetClient) {
    this.repository = repository;
    this.audit = audit;
    this.assetClient = assetClient;
  }

  @Transactional
  public Location create(CreateLocationRequest request, String actor) {
    CallerContext.requireAssetOperator();
    TenantContext.requireAllowed(request.clientId());
    if (repository.existsByQrTag(request.qrTag())) {
      throw new QrTagTakenException(request.qrTag());
    }
    Location location =
        new Location(request.clientId(), request.kind(), request.label(), request.qrTag());
    location.setBuilding(request.building());
    location.setFloor(request.floor());
    Location saved = repository.save(location);
    audit.record(
        saved.getClientId(),
        actor,
        "LOCATION_CREATED",
        saved.getId(),
        "added " + saved.getKind() + " " + saved.getLabel() + " (" + saved.getQrTag() + ")",
        null);
    return saved;
  }

  /** Corrects a location. Null fields are left as they are. */
  @Transactional
  public Location update(Long id, UpdateLocationRequest request, String actor) {
    CallerContext.requireAssetOperator();
    Location location = getById(id);
    TenantContext.requireAllowed(location.getClientId());

    applyQrTag(location, request.qrTag());
    applyLabel(location, request.label());
    applyPlacement(location, request);

    audit.record(
        location.getClientId(),
        actor,
        "LOCATION_UPDATED",
        location.getId(),
        "updated " + location.getKind() + " " + location.getLabel(),
        null);
    return location;
  }

  /**
   * Deletes a location, refusing while anything is placed on it.
   *
   * <p>A hard delete rather than the retirement people get, because the thing this is for is a
   * location created by mistake - and a desk carries no history of its own the way a person does.
   * What it can carry is equipment, and deleting it out from under a monitor would leave that
   * monitor pointing at a location nobody can look up. So asset-service is asked first, and says no
   * by refusing to answer if it is unreachable.
   *
   * <p>Somebody seated here is not a blocker: a person keeps their own desk id, the desk map stops
   * showing them, and nothing is lost. The console warns before it comes to this.
   */
  @Transactional
  public void delete(Long id, String actor) {
    CallerContext.requireAssetOperator();
    Location location = getById(id);
    TenantContext.requireAllowed(location.getClientId());

    List<Long> placed = assetClient.assetIdsPlacedAt(location.getClientId(), location.getId());
    if (!placed.isEmpty()) {
      throw new LocationOccupiedException(location.getLabel(), placed.size());
    }

    audit.record(
        location.getClientId(),
        actor,
        "LOCATION_DELETED",
        location.getId(),
        "deleted "
            + location.getKind()
            + " "
            + location.getLabel()
            + " ("
            + location.getQrTag()
            + ")",
        null);
    repository.delete(location);
  }

  /** The tag is what a phone reads off the wall, so it stays unique the way creation demands. */
  private void applyQrTag(Location location, String qrTag) {
    if (qrTag == null || qrTag.equals(location.getQrTag())) {
      return;
    }
    if (repository.existsByQrTag(qrTag)) {
      throw new QrTagTakenException(qrTag);
    }
    location.setQrTag(qrTag);
  }

  private static void applyLabel(Location location, String label) {
    if (label != null && !label.isBlank()) {
      location.setLabel(label.trim());
    }
  }

  /** Building and floor are the two a caller may legitimately want to empty, so blank clears. */
  private static void applyPlacement(Location location, UpdateLocationRequest request) {
    if (request.building() != null) {
      location.setBuilding(blankToNull(request.building()));
    }
    if (request.floor() != null) {
      location.setFloor(blankToNull(request.floor()));
    }
  }

  private static String blankToNull(String value) {
    return value.isBlank() ? null : value.trim();
  }

  @Transactional(readOnly = true)
  public List<Location> list(Long clientId, LocationKind kind) {
    return kind == null
        ? repository.findByClientIdOrderByLabelAsc(clientId)
        : repository.findByClientIdAndKind(clientId, kind);
  }

  /** Locations, a page at a time. The desk map still uses the unpaged list; see PagedLocations. */
  @Transactional(readOnly = true)
  public Page<Location> listPage(Long clientId, LocationKind kind, Pageable pageable) {
    return kind == null
        ? repository.findByClientId(clientId, pageable)
        : repository.findByClientIdAndKind(clientId, kind, pageable);
  }

  @Transactional(readOnly = true)
  public Location getById(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new LocationNotFoundException("No location with id '" + id + "'"));
  }

  @Transactional(readOnly = true)
  public Location getByQrTag(String qrTag) {
    return repository
        .findByQrTag(qrTag)
        .orElseThrow(
            () -> new LocationNotFoundException("No location with QR tag '" + qrTag + "'"));
  }
}
