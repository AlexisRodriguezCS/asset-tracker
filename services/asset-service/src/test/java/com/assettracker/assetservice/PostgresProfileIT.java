package com.assettracker.assetservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.assettracker.assetservice.entity.Asset;
import com.assettracker.assetservice.entity.AssetStatus;
import com.assettracker.assetservice.repository.AssetRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The {@code prod} path end to end on a real Postgres: every Flyway migration applies and Hibernate
 * {@code ddl-auto: validate} agrees the entities match the schema (a context that starts is the
 * assertion). Skipped automatically where Docker is unavailable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("prod")
@Testcontainers(disabledWithoutDocker = true)
class PostgresProfileIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    // this is a persistence test - keep the discovery client and config server out of it
    registry.add("eureka.client.enabled", () -> "false");
    registry.add("spring.cloud.config.enabled", () -> "false");
    registry.add("spring.config.import", () -> "");
  }

  @Autowired Flyway flyway;
  @Autowired AssetRepository assets;

  @Test
  void everyVersionedMigrationApplied() {
    long applied =
        java.util.Arrays.stream(flyway.info().applied())
            .filter(m -> m.getVersion() != null)
            .count();
    assertThat(applied).isEqualTo(6);
  }

  @Test
  void theSchemaRoundTrips() {
    Asset saved = assets.save(new Asset(1L, "Laptop", "SN-IT-1", "IT-TAG-1"));

    assertThat(saved.getId()).isNotNull();
    assertThat(assets.findById(saved.getId()))
        .get()
        .satisfies(
            a -> {
              assertThat(a.getStatus()).isEqualTo(AssetStatus.IN_STOCK);
              assertThat(a.getAssetTag()).isEqualTo("IT-TAG-1");
            });
  }
}
