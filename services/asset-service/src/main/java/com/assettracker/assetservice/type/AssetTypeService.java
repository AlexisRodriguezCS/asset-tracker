package com.assettracker.assetservice.type;

import com.assettracker.assetservice.audit.AuditDetail;
import com.assettracker.assetservice.audit.AuditService;
import com.assettracker.assetservice.entity.Asset;
import com.assettracker.assetservice.entity.AssetType;
import com.assettracker.assetservice.repository.AssetRepository;
import com.assettracker.assetservice.service.AssetNotFoundException;
import com.assettracker.assetservice.type.AssetTypeInUseException.LinkedAsset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Add / list / remove the type names a client uses. */
@Service
public class AssetTypeService {

  private final AssetTypeRepository types;
  private final AssetRepository assets;
  private final AuditService audit;

  public AssetTypeService(AssetTypeRepository types, AssetRepository assets, AuditService audit) {
    this.types = types;
    this.assets = assets;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<AssetType> list(Long clientId) {
    return types.findByClientIdOrderByName(clientId);
  }

  /** Assets currently on a type name - lets the console pre-check before offering delete. */
  @Transactional(readOnly = true)
  public List<LinkedAsset> usage(Long id) {
    AssetType type = get(id);
    return links(type.getClientId(), type.getName());
  }

  @Transactional
  public AssetType create(Long clientId, String rawName, String actor) {
    String name = rawName == null ? "" : rawName.trim();
    if (name.isEmpty()) {
      throw new IllegalArgumentException("type name is required");
    }
    if (types.existsByClientIdAndNameIgnoreCase(clientId, name)) {
      throw new AssetTypeExistsException(name);
    }
    AssetType saved = types.save(new AssetType(clientId, name));
    audit.record(clientId, actor, "TYPE_CREATED", saved.getId(), "added type " + name, null);
    return saved;
  }

  /**
   * Delete a type. Blocked while assets still reference the name, unless {@code reassignTo} names
   * another of the client's types to move them to first.
   */
  @Transactional
  public void delete(Long id, String reassignTo, String actor) {
    AssetType type = get(id);
    List<Asset> onType = assets.findByClientIdAndType(type.getClientId(), type.getName());

    if (!onType.isEmpty()) {
      if (reassignTo == null || reassignTo.isBlank()) {
        throw new AssetTypeInUseException(type.getName(), toLinks(onType));
      }
      AssetType target =
          types
              .findByClientIdAndNameIgnoreCase(type.getClientId(), reassignTo.trim())
              .orElseThrow(
                  () -> new IllegalArgumentException("no type '" + reassignTo + "' to move to"));
      onType.forEach(a -> a.setType(target.getName()));
      assets.saveAll(onType);
    }

    types.delete(type);
    audit.record(
        type.getClientId(),
        actor,
        "TYPE_DELETED",
        id,
        "removed type " + type.getName(),
        onType.isEmpty() ? null : AuditDetail.of("reassigned", onType.size()));
  }

  private AssetType get(Long id) {
    return types
        .findById(id)
        .orElseThrow(() -> new AssetNotFoundException("No type with id '" + id + "'"));
  }

  private List<LinkedAsset> links(Long clientId, String name) {
    return toLinks(assets.findByClientIdAndType(clientId, name));
  }

  private static List<LinkedAsset> toLinks(List<Asset> list) {
    return list.stream()
        .map(
            a ->
                new LinkedAsset(
                    a.getId(), a.getAssetTag(), a.getMake(), a.getModel(), a.getStatus().name()))
        .toList();
  }
}
