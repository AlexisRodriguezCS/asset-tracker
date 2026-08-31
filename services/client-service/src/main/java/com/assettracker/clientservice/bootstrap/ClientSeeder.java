package com.assettracker.clientservice.bootstrap;

import com.assettracker.clientservice.entity.Client;
import com.assettracker.clientservice.repository.ClientRepository;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Seeds a few demo tenants on an empty database (dev only). */
@Component
@Profile("!prod")
public class ClientSeeder implements CommandLineRunner {

  private final ClientRepository repository;

  public ClientSeeder(ClientRepository repository) {
    this.repository = repository;
  }

  @Override
  public void run(String... args) {
    if (repository.count() > 0) {
      return;
    }
    repository.saveAll(
        List.of(
            new Client("Acme Corp", "acme"),
            new Client("Globex", "globex"),
            new Client("Initech", "initech")));
  }
}
