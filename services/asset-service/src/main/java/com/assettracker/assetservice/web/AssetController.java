package com.assettracker.assetservice.web;

import com.assettracker.assetservice.entity.AssetStatus;
import com.assettracker.assetservice.entity.HolderType;
import com.assettracker.assetservice.service.AssetReportService;
import com.assettracker.assetservice.service.AssetService;
import com.assettracker.assetservice.service.AssetSummaryService;
import com.assettracker.assetservice.web.dto.AssetAttention;
import com.assettracker.assetservice.web.dto.AssetReport;
import com.assettracker.assetservice.web.dto.AssetResponse;
import com.assettracker.assetservice.web.dto.AssetStats;
import com.assettracker.assetservice.web.dto.AssignRequest;
import com.assettracker.assetservice.web.dto.ChangeStatusRequest;
import com.assettracker.assetservice.web.dto.CreateAssetRequest;
import com.assettracker.assetservice.web.dto.PagedAssets;
import com.assettracker.assetservice.web.dto.TypeUsage;
import com.assettracker.assetservice.web.dto.UpdateAssetRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

  private static final int DEFAULT_PAGE_SIZE = 50;

  private static final String ACTOR = "X-User-Id";

  private final AssetService service;
  private final AssetSummaryService summary;
  private final AssetReportService reports;

  public AssetController(
      AssetService service, AssetSummaryService summary, AssetReportService reports) {
    this.service = service;
    this.summary = summary;
    this.reports = reports;
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

  /**
   * The catalog list, paginated. Separate from the unpaged {@code GET /assets} rather than changing
   * its shape: aggregating callers (dashboard, reports, type counts) legitimately want every row,
   * and a response that is sometimes an array and sometimes an envelope is worse than two honest
   * endpoints. Size is clamped so a caller cannot ask for the whole tenant through this route.
   */
  @GetMapping("/paged")
  public PagedAssets searchPage(
      @RequestParam Long clientId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) AssetStatus status,
      @RequestParam(required = false) HolderType holderType,
      @RequestParam(required = false) Long holderId,
      @RequestParam(required = false) String tag,
      @PageableDefault(
              size = DEFAULT_PAGE_SIZE,
              sort = {"type", "assetTag"})
          Pageable pageable) {
    return PagedAssets.from(
        service.searchPage(clientId, type, status, holderType, holderId, tag, pageable));
  }

  /**
   * Counts for the console's summary strips. Exists so those pages stop fetching the whole catalog
   * to count it - that cost a 416kB payload per render on four pages.
   */
  @GetMapping("/stats")
  public AssetStats stats(
      @RequestParam Long clientId, @RequestParam(defaultValue = "60") int warrantySoonDays) {
    return summary.stats(clientId, warrantySoonDays);
  }

  /**
   * The dashboard's "needs attention" panel: four buckets with counts and short previews, in one
   * call. Replaces fetching the whole catalog and bucketing it in the render.
   */
  @GetMapping("/attention")
  public AssetAttention attention(
      @RequestParam Long clientId,
      @RequestParam(defaultValue = "5") int sampleSize,
      @RequestParam(defaultValue = "60") int warrantySoonDays) {
    return summary.attention(clientId, sampleSize, warrantySoonDays);
  }

  /**
   * Usage per type - count plus a few example assets - for the type manager's delete confirmation.
   * Types come from the caller because it has already loaded the catalog of them.
   */
  @GetMapping("/types/usage")
  public List<TypeUsage> typeUsage(
      @RequestParam Long clientId,
      @RequestParam List<String> type,
      @RequestParam(defaultValue = "8") int sampleSize) {
    return summary.typeUsage(clientId, type, sampleSize);
  }

  /**
   * Every rollup the reports page draws, in one call: counts by type / status / condition, the
   * warranty split, fleet value, replacement slots, who holds what, and the lifecycle and
   * break-and-loss tallies from the audit trail. It was the last page fetching the whole catalog -
   * and the whole trail - to group in the render.
   */
  @GetMapping("/reports")
  public AssetReport report(
      @RequestParam Long clientId,
      @RequestParam(defaultValue = "60") int warrantySoonDays,
      @RequestParam(defaultValue = "8") int topSlots) {
    return reports.report(clientId, warrantySoonDays, topSlots);
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
