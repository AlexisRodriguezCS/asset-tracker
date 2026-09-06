package com.assettracker.assetservice.imports;

import com.assettracker.assetservice.entity.Asset;
import com.assettracker.assetservice.repository.AssetRepository;
import com.assettracker.assetservice.type.AssetTypeRepository;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A client's existing assets keyed by tag+type, plus the type names it already has.
 *
 * <p>Loaded once per import rather than queried per row: a thousand-row sheet was a thousand
 * lookups. Held as its own type because the import service should read as "plan these rows against
 * what is already there", not as bookkeeping about maps.
 *
 * @param byTagType existing assets, so a re-upload updates rather than duplicating
 * @param typeNames lower-cased type names, including any the import is about to create
 */
record ImportIndex(Map<String, Asset> byTagType, Set<String> typeNames) {

  /** Separates the key's halves; a NUL cannot occur in a tag or type, so it cannot collide. */
  private static final char KEY_SEPARATOR = '\0';

  static ImportIndex load(Long clientId, AssetRepository assets, AssetTypeRepository types) {
    Map<String, Asset> byTagType = new LinkedHashMap<>();
    for (Asset a : assets.findByClientId(clientId)) {
      byTagType.putIfAbsent(key(a.getAssetTag(), a.getType()), a);
    }
    Set<String> typeNames = new HashSet<>();
    types
        .findByClientIdOrderByName(clientId)
        .forEach(t -> typeNames.add(t.getName().toLowerCase(Locale.ROOT)));
    return new ImportIndex(byTagType, typeNames);
  }

  /** The upsert key: one client's unit on a given tag and type. */
  static String key(String tag, String type) {
    return tag + KEY_SEPARATOR + type;
  }

  Asset existing(String tag, String type) {
    return byTagType.get(key(tag, type));
  }
}
