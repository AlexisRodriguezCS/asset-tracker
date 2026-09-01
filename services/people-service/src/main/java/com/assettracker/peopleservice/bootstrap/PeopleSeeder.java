package com.assettracker.peopleservice.bootstrap;

import com.assettracker.peopleservice.entity.Person;
import com.assettracker.peopleservice.repository.PersonRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds demo employees for every demo client on an empty database (dev only). Some have a home desk
 * (a soft reference to a DESK location id from {@code LocationSeeder}); others deliberately have
 * none.
 */
@Component
@Profile("!prod")
public class PeopleSeeder implements CommandLineRunner {

  // Desk location ids, matching LocationSeeder insert order on a fresh database.
  private static final Row[] ROWS = {
    new Row(1L, "Dana Reyes", "dana.reyes@acme.example", "Engineering", 4L),
    new Row(1L, "Sam Okafor", "sam.okafor@acme.example", "Design", null),
    new Row(1L, "Priya Nair", "priya.nair@acme.example", "Finance", 6L),
    new Row(1L, "Leo Martins", "leo.martins@acme.example", "Support", null),
    new Row(2L, "Maya Chen", "maya.chen@globex.example", "Engineering", 20L),
    new Row(2L, "Omar Farouk", "omar.farouk@globex.example", "Sales", null),
    new Row(2L, "Nina Patel", "nina.patel@globex.example", "Operations", 22L),
    new Row(2L, "Tariq Bello", "tariq.bello@globex.example", "Support", null),
    new Row(3L, "Grace Liu", "grace.liu@initech.example", "Finance", 30L),
    new Row(3L, "Victor Novak", "victor.novak@initech.example", "IT", null),
    new Row(3L, "Aria Sundqvist", "aria.sundqvist@initech.example", "People Ops", 32L),
  };

  private final PersonRepository repository;

  public PeopleSeeder(PersonRepository repository) {
    this.repository = repository;
  }

  @Override
  public void run(String... args) {
    if (repository.count() > 0) {
      return;
    }
    List<Person> seed = new ArrayList<>();
    for (Row r : ROWS) {
      Person p = new Person(r.clientId(), r.name(), r.email(), r.department());
      p.setDeskId(r.deskId());
      seed.add(p);
    }
    repository.saveAll(seed);
  }

  private record Row(long clientId, String name, String email, String department, Long deskId) {}
}
