package com.assettracker.peopleservice.bootstrap;

import com.assettracker.peopleservice.entity.Person;
import com.assettracker.peopleservice.repository.PersonRepository;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Seeds demo employees for client 1 (Acme) on an empty database (dev only). */
@Component
@Profile("!prod")
public class PeopleSeeder implements CommandLineRunner {

  private static final long ACME = 1L;

  private final PersonRepository repository;

  public PeopleSeeder(PersonRepository repository) {
    this.repository = repository;
  }

  @Override
  public void run(String... args) {
    if (repository.count() > 0) {
      return;
    }
    repository.saveAll(
        List.of(
            new Person(ACME, "Dana Reyes", "dana.reyes@acme.example", "Engineering"),
            new Person(ACME, "Sam Okafor", "sam.okafor@acme.example", "Design"),
            new Person(ACME, "Priya Nair", "priya.nair@acme.example", "Finance"),
            new Person(ACME, "Leo Martins", "leo.martins@acme.example", "Support")));
  }
}
