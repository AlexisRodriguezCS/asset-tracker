package com.assettracker.assetservice.bootstrap;

import com.assettracker.assetservice.entity.Asset;
import com.assettracker.assetservice.entity.AssetType;
import com.assettracker.assetservice.repository.AssetRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Seeds a spread of assets for client 1 (Acme), all in the stockroom (dev only). */
@Component
@Profile("!prod")
public class AssetSeeder implements CommandLineRunner {

  private static final long ACME = 1L;

  private final AssetRepository repository;

  public AssetSeeder(AssetRepository repository) {
    this.repository = repository;
  }

  @Override
  public void run(String... args) {
    if (repository.count() > 0) {
      return;
    }
    List<Asset> seed = new ArrayList<>();
    add(seed, AssetType.LAPTOP, "Apple", "MacBook Pro 14 M3", 6, 249900, "L");
    add(seed, AssetType.LAPTOP, "Dell", "Latitude 7440", 4, 149900, "L");
    add(seed, AssetType.TABLET, "Apple", "iPad Air 11", 5, 79900, "T");
    add(seed, AssetType.MONITOR, "Dell", "U2723QE 27\" 4K", 8, 54900, "M");
    add(seed, AssetType.DOCK, "CalDigit", "TS4", 6, 39900, "D");
    add(seed, AssetType.CHARGER, "Apple", "96W USB-C", 10, 7900, "C");
    add(seed, AssetType.CABLE, "Anker", "USB-C 2m", 20, 1900, "K");
    repository.saveAll(seed);
  }

  private static void add(
      List<Asset> out,
      AssetType type,
      String make,
      String model,
      int qty,
      long costCents,
      String p) {
    for (int i = 1; i <= qty; i++) {
      Asset a =
          new Asset(
              ACME,
              type,
              make.toUpperCase()
                  + "-"
                  + model.replaceAll("\\s+", "")
                  + "-SN"
                  + String.format("%03d", i),
              "ACME-" + p + "-" + String.format("%03d", i));
      a.setMake(make);
      a.setModel(model);
      a.setPurchaseCostCents(costCents);
      a.setPurchaseDate(LocalDate.now().minusMonths(i));
      out.add(a);
    }
  }
}
