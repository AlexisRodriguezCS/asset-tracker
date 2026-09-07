package com.assettracker.assignmentservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
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
 * The {@code prod} path on a real Postgres: every Flyway migration applies and Hibernate {@code
 * ddl-auto: validate} agrees the entities match the schema (a context that starts is the
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
    registry.add("eureka.client.enabled", () -> "false");
    registry.add("spring.cloud.config.enabled", () -> "false");
    registry.add("spring.config.import", () -> "");
  }

  @Autowired Flyway flyway;

  /**
   * Every migration on the classpath actually ran. Counted against what Flyway found rather than a
   * literal, which used to be 3 and broke the moment a fourth was added - a test that has to be
   * edited whenever the thing it guards changes teaches people to edit it without looking.
   */
  @Test
  void everyVersionedMigrationApplied() {
    long onDisk = Arrays.stream(flyway.info().all()).filter(m -> m.getVersion() != null).count();
    long applied =
        Arrays.stream(flyway.info().applied()).filter(m -> m.getVersion() != null).count();

    assertThat(onDisk).isPositive();
    assertThat(applied).isEqualTo(onDisk);
  }
}
