package com.assettracker.assetservice.bootstrap;

import com.assettracker.assetservice.entity.AssetType;
import com.assettracker.assetservice.type.AssetTypeRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Gives each demo client the standard starter set of type names; techs edit the list from there.
 */
@Component
@Profile("!prod")
@Order(10)
public class AssetTypeSeeder implements CommandLineRunner {

  private static final long[] CLIENT_IDS = {1L, 2L, 3L};
  private static final String[] NAMES = {
    "Laptop",
    "Tablet",
    "Phone",
    "Monitor",
    "Dock",
    "Charger",
    "Cable",
    "Hotspot",
    "Peripheral",
    "Other"
  };

  private final AssetTypeRepository repository;

  public AssetTypeSeeder(AssetTypeRepository repository) {
    this.repository = repository;
  }

  @Override
  public void run(String... args) {
    if (repository.count() > 0) {
      return;
    }
    List<AssetType> seed = new ArrayList<>();
    for (long clientId : CLIENT_IDS) {
      for (String name : NAMES) {
        seed.add(new AssetType(clientId, name));
      }
    }
    repository.saveAll(seed);
  }
}
