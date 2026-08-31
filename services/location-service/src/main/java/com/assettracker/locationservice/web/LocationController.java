package com.assettracker.locationservice.web;

import com.assettracker.locationservice.entity.LocationKind;
import com.assettracker.locationservice.service.LocationService;
import com.assettracker.locationservice.web.dto.CreateLocationRequest;
import com.assettracker.locationservice.web.dto.LocationResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for locations. Lists are always scoped to a client. */
@RestController
@RequestMapping("/locations")
public class LocationController {

  private final LocationService service;

  public LocationController(LocationService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<LocationResponse> create(
      @Valid @RequestBody CreateLocationRequest request) {
    LocationResponse body = LocationResponse.from(service.create(request));
    return ResponseEntity.created(URI.create("/locations/" + body.id())).body(body);
  }

  @GetMapping
  public List<LocationResponse> list(
      @RequestParam Long clientId, @RequestParam(required = false) LocationKind kind) {
    return service.list(clientId, kind).stream().map(LocationResponse::from).toList();
  }

  @GetMapping("/{id}")
  public LocationResponse getById(@PathVariable Long id) {
    return LocationResponse.from(service.getById(id));
  }

  /** Resolve a scanned sticker code to its location - the mobile app's entry point. */
  @GetMapping("/by-qr")
  public LocationResponse getByQr(@RequestParam String tag) {
    return LocationResponse.from(service.getByQrTag(tag));
  }
}
