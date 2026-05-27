package com.camelbytes.sitrep;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SitrepApplicationTests {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("POSTGRES_URL", TestcontainersConfiguration.postgres::getJdbcUrl);
    registry.add("APP_USER_USERNAME", TestcontainersConfiguration.postgres::getUsername);
    registry.add("APP_USER_PASSWORD", TestcontainersConfiguration.postgres::getPassword);
    registry.add("FLYWAY_URL", TestcontainersConfiguration.postgres::getJdbcUrl);
    registry.add("FLYWAY_USERNAME", TestcontainersConfiguration.postgres::getUsername);
    registry.add("FLYWAY_PASSWORD", TestcontainersConfiguration.postgres::getPassword);
  }

  @Test
  void contextLoads() {}
}
