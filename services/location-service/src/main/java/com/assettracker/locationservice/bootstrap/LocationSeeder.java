package com.assettracker.locationservice.bootstrap;

import com.assettracker.locationservice.entity.Location;
import com.assettracker.locationservice.entity.LocationKind;
import com.assettracker.locationservice.repository.LocationRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Seeds a site, a floor of rooms, and a row of desks for client 1 (dev only). */
@Component
@Profile("!prod")
public class LocationSeeder implements CommandLineRunner {

  private static final long ACME = 1L;
  private static final int DESK_COUNT = 12;

  private final LocationRepository repository;

  public LocationSeeder(LocationRepository repository) {
    this.repository = repository;
  }

  @Override
  public void run(String... args) {
    if (repository.count() > 0) {
      return;
    }
    List<Location> seed = new ArrayList<>();
    seed.add(new Location(ACME, LocationKind.SITE, "HQ", "ACME-SITE-HQ"));
    seed.add(
        withPlace(new Location(ACME, LocationKind.ROOM, "Stockroom", "ACME-RM-STK"), "HQ", "1"));
    for (int i = 1; i <= DESK_COUNT; i++) {
      String n = String.format("%03d", i);
      seed.add(
          withPlace(new Location(ACME, LocationKind.DESK, "Desk " + n, "ACME-D-" + n), "HQ", "2"));
    }
    repository.saveAll(seed);
  }

  private static Location withPlace(Location l, String building, String floor) {
    l.setBuilding(building);
    l.setFloor(floor);
    return l;
  }
}
