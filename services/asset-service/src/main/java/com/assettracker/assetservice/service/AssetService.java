package com.assettracker.assetservice.service;

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

/** Business operations for assets, including the guarded custody transitions. */
@Service
public class AssetService {

  private final AssetRepository repository;

  public AssetService(AssetRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public Asset create(CreateAssetRequest request) {
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
    return repository.save(asset);
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
  public Asset update(Long id, UpdateAssetRequest request) {
    Asset asset = getById(id);
    if (request.make() != null) {
      asset.setMake(request.make());
    }
    if (request.model() != null) {
      asset.setModel(request.model());
    }
    if (request.notes() != null) {
      asset.setNotes(request.notes());
    }
    return asset;
  }

  /** Called by assignment-service. Throws {@code AlreadyAssignedException} (409) if not free. */
  @Transactional
  public Asset assign(Long id, AssignRequest request) {
    Asset asset = getById(id);
    asset.assignTo(request.holderType(), request.holderId());
    return asset;
  }

  /** Called by assignment-service on check-in. */
  @Transactional
  public Asset returnToStock(Long id) {
    Asset asset = getById(id);
    asset.returnToStock();
    return asset;
  }

  @Transactional
  public Asset changeStatus(Long id, AssetStatus status) {
    Asset asset = getById(id);
    asset.setStatus(status);
    return asset;
  }

  private static AssetNotFoundException notFound(String field, String value) {
    return new AssetNotFoundException("No asset with " + field + " '" + value + "'");
  }
}
