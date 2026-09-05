package com.assettracker.assetservice.service;

import com.assettracker.assetservice.audit.AuditDetail;
import com.assettracker.assetservice.audit.AuditService;
import com.assettracker.assetservice.entity.Asset;
import com.assettracker.assetservice.entity.AssetStatus;
import com.assettracker.assetservice.entity.HolderType;
import com.assettracker.assetservice.repository.AssetRepository;
import com.assettracker.assetservice.web.CallerContext;
import com.assettracker.assetservice.web.TenantContext;
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
    TenantContext.requireAllowed(request.clientId());
    if (repository.existsActiveWithTag(
        request.clientId(), request.assetTag(), request.type(), AssetStatus.ACTIVE)) {
      throw new AssetTagTakenException(request.assetTag());
    }
    Asset asset =
        new Asset(request.clientId(), request.type(), request.serialNumber(), request.assetTag());
    asset.setMake(request.make());
    asset.setModel(request.model());
    asset.setCondition(request.condition());
    asset.setPurchaseDate(request.purchaseDate());
    asset.setDeployedOn(request.deployedOn());
    asset.setWarrantyEndsOn(request.warrantyEndsOn());
    asset.setPurchaseCostCents(request.purchaseCostCents());
    asset.setNotes(request.notes());
    asset.setSupersedesAssetId(resolveSuperseded(request));
    Asset saved = repository.save(asset);
    audit.record(
        saved.getClientId(),
        actor,
        "ASSET_CREATED",
        saved.getId(),
        "added " + saved.describe(),
        saved.getSupersedesAssetId() == null
            ? null
            : AuditDetail.of("supersedes", saved.getSupersedesAssetId()));
    return saved;
  }

  /**
   * Validates {@code supersedesAssetId} (if given): it must be an asset of the same client carrying
   * the same tag and type - a replacement is the next unit in the same slot, not an arbitrary link.
   */
  private Long resolveSuperseded(CreateAssetRequest request) {
    if (request.supersedesAssetId() == null) {
      return null;
    }
    Asset superseded =
        repository
            .findById(request.supersedesAssetId())
            .filter(s -> s.getClientId().equals(request.clientId()))
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "no asset " + request.supersedesAssetId() + " for this client to replace"));
    if (!superseded.getAssetTag().equals(request.assetTag())
        || !superseded.getType().equals(request.type())) {
      throw new IllegalArgumentException(
          "a replacement must carry the same tag and type as the unit it supersedes");
    }
    return request.supersedesAssetId();
  }

  /**
   * The catalog query behind every list view, narrowed to what the caller may see.
   *
   * <p>An ordinary employee ({@code USER}) is confined to the assets held by their own person
   * record, whatever filter they asked for - the request cannot widen it, and a caller with no
   * person record simply sees nothing rather than everything.
   */
  @Transactional(readOnly = true)
  public List<Asset> search(
      Long clientId,
      String type,
      AssetStatus status,
      HolderType holderType,
      Long holderId,
      String assetTag) {
    TenantContext.requireAllowed(clientId);
    if (CallerContext.isSelfServiceUser()) {
      Long self = CallerContext.personId();
      if (self == null) {
        return List.of();
      }
      return repository.search(clientId, type, status, HolderType.PERSON, self, assetTag);
    }
    return repository.search(clientId, type, status, holderType, holderId, assetTag);
  }

  /** True when the caller is allowed to see this particular asset. */
  private boolean maySee(Asset asset) {
    if (!TenantContext.allows(asset.getClientId())) {
      return false;
    }
    if (!CallerContext.isSelfServiceUser()) {
      return true;
    }
    Long self = CallerContext.personId();
    return self != null
        && asset.getHolderType() == HolderType.PERSON
        && self.equals(asset.getHolderId());
  }

  @Transactional(readOnly = true)
  public List<Asset> heldBy(HolderType holderType, Long holderId) {
    return repository.findByHolderTypeAndHolderId(holderType, holderId);
  }

  @Transactional(readOnly = true)
  public Asset getById(Long id) {
    Asset asset = repository.findById(id).orElseThrow(() -> notFound("id", String.valueOf(id)));
    if (!maySee(asset)) {
      // 404 rather than 403: whether an id exists is itself information the caller lacks.
      throw notFound("id", String.valueOf(id));
    }
    return asset;
  }

  /** The current unit on a tag: the active one if any, otherwise the most recent retired unit. */
  @Transactional(readOnly = true)
  public Asset getByTag(String tag) {
    return repository
        .findFirstByAssetTagAndStatusInOrderByIdDesc(tag, AssetStatus.ACTIVE)
        .or(() -> repository.findFirstByAssetTagOrderByIdDesc(tag))
        .orElseThrow(() -> notFound("tag", tag));
  }

  @Transactional
  public Asset update(Long id, UpdateAssetRequest request, String actor) {
    Asset asset = getById(id);
    TenantContext.requireAllowed(asset.getClientId());
    String before = "make=" + asset.getMake() + " model=" + asset.getModel();
    setIfPresent(request.make(), asset::setMake);
    setIfPresent(request.model(), asset::setModel);
    setIfPresent(request.notes(), asset::setNotes);
    setIfPresent(request.condition(), asset::setCondition);
    setIfPresent(request.serialNumber(), asset::setSerialNumber);
    setIfPresent(request.purchaseDate(), asset::setPurchaseDate);
    setIfPresent(request.deployedOn(), asset::setDeployedOn);
    setIfPresent(request.warrantyEndsOn(), asset::setWarrantyEndsOn);
    audit.record(
        asset.getClientId(),
        actor,
        "ASSET_UPDATED",
        asset.getId(),
        "edited " + asset.describe(),
        AuditDetail.of(
            "before", before, "after", "make=" + asset.getMake() + " model=" + asset.getModel()));
    return asset;
  }

  /** Called by assignment-service. Throws {@code AlreadyAssignedException} (409) if not free. */
  @Transactional
  public Asset assign(Long id, AssignRequest request, String actor) {
    Asset asset = getById(id);
    TenantContext.requireAllowed(asset.getClientId());
    asset.assignTo(request.holderType(), request.holderId());
    audit.record(
        asset.getClientId(),
        actor,
        "ASSET_ASSIGNED",
        asset.getId(),
        "assigned " + asset.describe() + " to " + request.holderType() + " " + request.holderId(),
        null);
    return asset;
  }

  /** Called by assignment-service on check-in. */
  @Transactional
  public Asset returnToStock(Long id, String actor) {
    Asset asset = getById(id);
    TenantContext.requireAllowed(asset.getClientId());
    Long from = asset.getHolderId();
    asset.returnToStock();
    audit.record(
        asset.getClientId(),
        actor,
        "ASSET_RETURNED",
        asset.getId(),
        "returned " + asset.describe() + " to stock" + (from == null ? "" : " from holder " + from),
        null);
    return asset;
  }

  @Transactional
  public Asset changeStatus(Long id, AssetStatus status, String actor) {
    Asset asset = getById(id);
    TenantContext.requireAllowed(asset.getClientId());
    AssetStatus before = asset.getStatus();
    asset.setStatus(status);
    audit.record(
        asset.getClientId(),
        actor,
        "ASSET_STATUS_" + status.name(),
        asset.getId(),
        "changed " + asset.describe() + " status " + before + " -> " + status,
        null);
    return asset;
  }

  private static <T> void setIfPresent(T value, java.util.function.Consumer<T> setter) {
    if (value != null) {
      setter.accept(value);
    }
  }

  private static AssetNotFoundException notFound(String field, String value) {
    return new AssetNotFoundException("No asset with " + field + " '" + value + "'");
  }
}
