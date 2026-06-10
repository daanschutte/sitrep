package dev.bravozulu.sitrep;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTests {
  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("POSTGRES_URL", TestcontainersConfiguration.postgres::getJdbcUrl);
    registry.add("POSTGRES_USERNAME", TestcontainersConfiguration.postgres::getUsername);
    registry.add("POSTGRES_PASSWORD", TestcontainersConfiguration.postgres::getPassword);
    registry.add("APP_USER_USERNAME", TestcontainersConfiguration.postgres::getUsername);
    registry.add("APP_USER_PASSWORD", TestcontainersConfiguration.postgres::getPassword);
  }
}
