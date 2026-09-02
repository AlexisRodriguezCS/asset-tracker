package com.assettracker.assetservice.web;

import com.assettracker.assetservice.entity.AssetStatus;
import com.assettracker.assetservice.entity.HolderType;
import com.assettracker.assetservice.service.AssetService;
import com.assettracker.assetservice.web.dto.AssetResponse;
import com.assettracker.assetservice.web.dto.AssignRequest;
import com.assettracker.assetservice.web.dto.ChangeStatusRequest;
import com.assettracker.assetservice.web.dto.CreateAssetRequest;
import com.assettracker.assetservice.web.dto.UpdateAssetRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for assets. The single {@code GET /assets} query drives every catalog view - "all
 * laptops" ({@code ?type=LAPTOP}), "what's on desk 14" ({@code ?holderType=LOCATION&holderId=14}),
 * "in for repair" ({@code ?status=IN_REPAIR}). Every write takes the acting tech's identity from
 * the {@code X-User-Id} header the gateway forwards.
 */
@RestController
@RequestMapping("/assets")
public class AssetController {

  private static final String ACTOR = "X-User-Id";

  private final AssetService service;

  public AssetController(AssetService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<AssetResponse> create(
      @Valid @RequestBody CreateAssetRequest request,
      @RequestHeader(value = ACTOR, defaultValue = "system") String actor) {
    AssetResponse body = AssetResponse.from(service.create(request, actor));
    return ResponseEntity.created(URI.create("/assets/" + body.id())).body(body);
  }

  @GetMapping
  public List<AssetResponse> search(
      @RequestParam Long clientId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) AssetStatus status,
      @RequestParam(required = false) HolderType holderType,
      @RequestParam(required = false) Long holderId,
      @RequestParam(required = false) String tag) {
    return service.search(clientId, type, status, holderType, holderId, tag).stream()
        .map(AssetResponse::from)
        .toList();
  }

  @GetMapping("/{id}")
  public AssetResponse getById(@PathVariable Long id) {
    return AssetResponse.from(service.getById(id));
  }

  @GetMapping("/by-tag")
  public AssetResponse getByTag(@RequestParam String tag) {
    return AssetResponse.from(service.getByTag(tag));
  }

  @PatchMapping("/{id}")
  public AssetResponse update(
      @PathVariable Long id,
      @RequestBody UpdateAssetRequest request,
      @RequestHeader(value = ACTOR, defaultValue = "system") String actor) {
    return AssetResponse.from(service.update(id, request, actor));
  }

  @PostMapping("/{id}/status")
  public AssetResponse changeStatus(
      @PathVariable Long id,
      @Valid @RequestBody ChangeStatusRequest request,
      @RequestHeader(value = ACTOR, defaultValue = "system") String actor) {
    return AssetResponse.from(service.changeStatus(id, request.status(), actor));
  }

  // --- called by assignment-service ---------------------------------------

  @PostMapping("/{id}/assign")
  public AssetResponse assign(
      @PathVariable Long id,
      @Valid @RequestBody AssignRequest request,
      @RequestHeader(value = ACTOR, defaultValue = "system") String actor) {
    return AssetResponse.from(service.assign(id, request, actor));
  }

  @PostMapping("/{id}/return")
  public AssetResponse returnToStock(
      @PathVariable Long id, @RequestHeader(value = ACTOR, defaultValue = "system") String actor) {
    return AssetResponse.from(service.returnToStock(id, actor));
  }
}
