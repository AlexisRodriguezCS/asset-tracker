package com.assettracker.assetservice.service;

import com.assettracker.assetservice.audit.AuditService;
import com.assettracker.assetservice.entity.Asset;
import com.assettracker.assetservice.entity.AssetStatus;
import com.assettracker.assetservice.entity.AssetType;
import com.assettracker.assetservice.entity.HolderType;
import com.assettracker.assetservice.repository.AssetRepository;
import com.assettracker.assetservice.web.dto.AssignRequest;
import com.assettracker.assetservice.web.dto.CreateAssetRequest;
import com.assettracker.assetservice.web.dto.UpdateAssetRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business operations for assets, including the guarded custody transitions. Every mutating method
 * takes an {@code actor} (the tech's identity, forwarded by the gateway) and writes an audit row in
 * the same transaction as the change.
 */
@Service
public class AssetService {

  private final AssetRepository repository;
  private final AuditService audit;

  public AssetService(AssetRepository repository, AuditService audit) {
    this.repository = repository;
    this.audit = audit;
  }

  @Transactional
  public Asset create(CreateAssetRequest request, String actor) {
    if (repository.existsByAssetTag(request.assetTag())) {
      throw new AssetTagTakenException(request.assetTag());
    }
    Asset asset =
        new Asset(request.clientId(), request.type(), request.serialNumber(), request.assetTag());
    asset.setMake(request.make());
    asset.setModel(request.model());
    asset.setPurchaseDate(request.purchaseDate());
    asset.setPurchaseCostCents(request.purchaseCostCents());
    asset.setNotes(request.notes());
    Asset saved = repository.save(asset);
    audit.record(
        saved.getClientId(),
        actor,
        "ASSET_CREATED",
        saved.getId(),
        "added " + describe(saved),
        null);
    return saved;
  }

  @Transactional(readOnly = true)
  public List<Asset> search(
      Long clientId, AssetType type, AssetStatus status, HolderType holderType, Long holderId) {
    return repository.search(clientId, type, status, holderType, holderId);
  }

  @Transactional(readOnly = true)
  public List<Asset> heldBy(HolderType holderType, Long holderId) {
    return repository.findByHolderTypeAndHolderId(holderType, holderId);
  }

  @Transactional(readOnly = true)
  public Asset getById(Long id) {
    return repository.findById(id).orElseThrow(() -> notFound("id", String.valueOf(id)));
  }

  @Transactional(readOnly = true)
  public Asset getByTag(String tag) {
    return repository.findByAssetTag(tag).orElseThrow(() -> notFound("tag", tag));
  }

  @Transactional
  public Asset update(Long id, UpdateAssetRequest request, String actor) {
    Asset asset = getById(id);
    String before = "make=" + asset.getMake() + " model=" + asset.getModel();
    if (request.make() != null) {
      asset.setMake(request.make());
    }
    if (request.model() != null) {
      asset.setModel(request.model());
    }
    if (request.notes() != null) {
      asset.setNotes(request.notes());
    }
    audit.record(
        asset.getClientId(),
        actor,
        "ASSET_UPDATED",
        asset.getId(),
        "edited " + describe(asset),
        "{\"before\":\""
            + before
            + "\",\"after\":\"make="
            + asset.getMake()
            + " model="
            + asset.getModel()
            + "\"}");
    return asset;
  }

  /** Called by assignment-service. Throws {@code AlreadyAssignedException} (409) if not free. */
  @Transactional
  public Asset assign(Long id, AssignRequest request, String actor) {
    Asset asset = getById(id);
    asset.assignTo(request.holderType(), request.holderId());
    audit.record(
        asset.getClientId(),
        actor,
        "ASSET_ASSIGNED",
        asset.getId(),
        "assigned " + describe(asset) + " to " + request.holderType() + " " + request.holderId(),
        null);
    return asset;
  }

  /** Called by assignment-service on check-in. */
  @Transactional
  public Asset returnToStock(Long id, String actor) {
    Asset asset = getById(id);
    Long from = asset.getHolderId();
    asset.returnToStock();
    audit.record(
        asset.getClientId(),
        actor,
        "ASSET_RETURNED",
        asset.getId(),
        "returned " + describe(asset) + " to stock" + (from == null ? "" : " from holder " + from),
        null);
    return asset;
  }

  @Transactional
  public Asset changeStatus(Long id, AssetStatus status, String actor) {
    Asset asset = getById(id);
    AssetStatus before = asset.getStatus();
    asset.setStatus(status);
    audit.record(
        asset.getClientId(),
        actor,
        "ASSET_STATUS_" + status.name(),
        asset.getId(),
        "changed " + describe(asset) + " status " + before + " -> " + status,
        null);
    return asset;
  }

  private static String describe(Asset a) {
    String makeModel =
        String.join(
            " ",
            java.util.stream.Stream.of(a.getMake(), a.getModel())
                .filter(s -> s != null && !s.isBlank())
                .toList());
    return (makeModel.isBlank() ? a.getType().name() : makeModel) + " (" + a.getAssetTag() + ")";
  }

  private static AssetNotFoundException notFound(String field, String value) {
    return new AssetNotFoundException("No asset with " + field + " '" + value + "'");
  }
}
