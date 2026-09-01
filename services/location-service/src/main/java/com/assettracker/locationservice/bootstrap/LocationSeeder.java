package com.assettracker.locationservice.bootstrap;

import com.assettracker.locationservice.entity.Location;
import com.assettracker.locationservice.entity.LocationKind;
import com.assettracker.locationservice.repository.LocationRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds sites, a stockroom, and desks for each demo client. Desks are spread across buildings and
 * floors so the console's building / floor map has something to group (dev only).
 *
 * <p>Insert order is fixed: {@code PeopleSeeder} and {@code AssetSeeder} reference the desk ids
 * this produces on a fresh database (IDENTITY from 1).
 */
@Component
@Profile("!prod")
public class LocationSeeder implements CommandLineRunner {

  private static final Site[] SITES = {
    new Site(
        1L,
        "ACME",
        List.of("HQ", "HQ", "Annex"),
        new DeskRow[] {
          new DeskRow("HQ", "2", 6), new DeskRow("HQ", "3", 4), new DeskRow("Annex", "1", 4),
        }),
    new Site(
        2L,
        "GLBX",
        List.of("Tower"),
        new DeskRow[] {new DeskRow("Tower", "1", 5), new DeskRow("Tower", "2", 3)}),
    new Site(3L, "INTC", List.of("Office"), new DeskRow[] {new DeskRow("Office", "1", 4)}),
  };

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
    for (Site site : SITES) {
      seedSite(seed, site);
    }
    repository.saveAll(seed);
  }

  private static void seedSite(List<Location> seed, Site site) {
    for (String building : distinct(site.buildings())) {
      seed.add(
          new Location(
              site.clientId(), LocationKind.SITE, building, site.code() + "-SITE-" + building));
    }
    seed.add(
        withPlace(
            new Location(site.clientId(), LocationKind.ROOM, "Stockroom", site.code() + "-RM-STK"),
            site.buildings().get(0),
            "1"));
    int desk = 0;
    for (DeskRow row : site.deskRows()) {
      for (int i = 0; i < row.count(); i++) {
        String n = String.format("%03d", ++desk);
        seed.add(
            withPlace(
                new Location(
                    site.clientId(), LocationKind.DESK, "Desk " + n, site.code() + "-D-" + n),
                row.building(),
                row.floor()));
      }
    }
  }

  private static List<String> distinct(List<String> values) {
    List<String> out = new ArrayList<>();
    for (String v : values) {
      if (!out.contains(v)) {
        out.add(v);
      }
    }
    return out;
  }

  private static Location withPlace(Location l, String building, String floor) {
    l.setBuilding(building);
    l.setFloor(floor);
    return l;
  }

  private record Site(long clientId, String code, List<String> buildings, DeskRow[] deskRows) {}

  private record DeskRow(String building, String floor, int count) {}
}
