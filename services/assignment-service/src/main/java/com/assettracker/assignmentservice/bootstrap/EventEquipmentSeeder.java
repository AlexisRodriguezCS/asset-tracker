package com.assettracker.assignmentservice.bootstrap;

import com.assettracker.assignmentservice.events.EventEquipment;
import com.assettracker.assignmentservice.events.EventEquipmentRepository;
import java.util.List;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * What each client lends at events, on a fresh dev stack.
 *
 * <p>These are small numbers on purpose. The whole point of the pool is that it runs out - two TVs
 * means the second request for two TVs on the same day is refused, and a demo where nothing ever
 * collides never shows that.
 */
@Component
@Profile("!prod")
@Order(90)
public class EventEquipmentSeeder implements CommandLineRunner {

  private static final Map<Long, Map<String, Integer>> POOLS =
      Map.of(
          1L, Map.of("Laptop", 1, "Charger", 1, "TV", 2, "Speaker", 2, "Mic", 1),
          2L, Map.of("Laptop", 2, "Charger", 2, "TV", 1, "Speaker", 2, "Mic", 2),
          3L, Map.of("Laptop", 1, "Charger", 2, "TV", 3, "Speaker", 1, "Mic", 1));

  private final EventEquipmentRepository repository;

  public EventEquipmentSeeder(EventEquipmentRepository repository) {
    this.repository = repository;
  }

  @Override
  public void run(String... args) {
    if (repository.count() > 0) {
      return;
    }
    List<EventEquipment> rows =
        POOLS.entrySet().stream()
            .flatMap(
                client ->
                    client.getValue().entrySet().stream()
                        .map(
                            item ->
                                new EventEquipment(
                                    client.getKey(), item.getKey(), item.getValue())))
            .toList();
    repository.saveAll(rows);
  }
}
