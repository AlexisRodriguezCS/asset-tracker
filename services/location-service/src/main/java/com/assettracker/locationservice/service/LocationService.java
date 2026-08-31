package com.assettracker.locationservice.service;

import com.assettracker.locationservice.entity.Location;
import com.assettracker.locationservice.entity.LocationKind;
import com.assettracker.locationservice.repository.LocationRepository;
import com.assettracker.locationservice.web.dto.CreateLocationRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business operations for locations (sites / rooms / desks). */
@Service
public class LocationService {

  private final LocationRepository repository;

  public LocationService(LocationRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public Location create(CreateLocationRequest request) {
    if (repository.existsByQrTag(request.qrTag())) {
      throw new QrTagTakenException(request.qrTag());
    }
    Location location =
        new Location(request.clientId(), request.kind(), request.label(), request.qrTag());
    location.setBuilding(request.building());
    location.setFloor(request.floor());
    return repository.save(location);
  }

  @Transactional(readOnly = true)
  public List<Location> list(Long clientId, LocationKind kind) {
    return kind == null
        ? repository.findByClientIdOrderByLabelAsc(clientId)
        : repository.findByClientIdAndKind(clientId, kind);
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
