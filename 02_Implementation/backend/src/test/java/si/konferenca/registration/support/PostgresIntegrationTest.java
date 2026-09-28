package si.konferenca.registration.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests against a real PostgreSQL 16 (Testcontainers). One container is
 * shared by all test classes; each test class cleans the tables it uses.
 */
public abstract class PostgresIntegrationTest {

  public static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("conference")
          .withUsername("conference")
          .withPassword("conference");

  public static final Path BACKUP_DIR;

  static {
    POSTGRES.start();
    try {
      BACKUP_DIR = Files.createTempDirectory("registration-backups");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    baseProperties(registry);
    registry.add("app.rate-limit.registration.requests", () -> "1000");
    registry.add("app.rate-limit.organizer.requests", () -> "1000");
  }

  /** Properties for tests that need a custom configuration and do not extend this class. */
  public static void baseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("app.backup.dir", BACKUP_DIR::toString);
    registry.add("app.recaptcha.test-mode", () -> "true");
    registry.add("app.organizer.password", () -> TestData.ORGANIZER_PASSWORD);
    registry.add("app.mail.organizer-emails", () -> TestData.ORGANIZER_EMAIL);
  }
}
