package com.assettracker.assetservice.type;

import com.assettracker.assetservice.type.AssetTypeInUseException.LinkedAsset;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manage the type names a client tracks. Sits under {@code /assets/types} so it rides the gateway's
 * existing {@code /api/assets/**} route (public GET, authenticated write).
 */
@RestController
@RequestMapping("/assets/types")
public class AssetTypeController {

  private static final String ACTOR = "X-User-Id";

  private final AssetTypeService service;

  public AssetTypeController(AssetTypeService service) {
    this.service = service;
  }

  @GetMapping
  public List<TypeView> list(@RequestParam Long clientId) {
    return service.list(clientId).stream().map(TypeView::from).toList();
  }

  @GetMapping("/{id}/usage")
  public List<LinkedAsset> usage(@PathVariable Long id) {
    return service.usage(id);
  }

  @PostMapping
  public ResponseEntity<TypeView> create(
      @Valid @RequestBody CreateTypeRequest request,
      @RequestHeader(value = ACTOR, defaultValue = "system") String actor) {
    TypeView body = TypeView.from(service.create(request.clientId(), request.name(), actor));
    return ResponseEntity.created(URI.create("/assets/types/" + body.id())).body(body);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable Long id,
      @RequestParam(required = false) String reassignTo,
      @RequestHeader(value = ACTOR, defaultValue = "system") String actor) {
    service.delete(id, reassignTo, actor);
    return ResponseEntity.noContent().build();
  }
}
