package si.konferenca.registration;

import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Full application context against a real PostgreSQL 16 (Testcontainers). SMTP is replaced by a
 * Mockito {@link JavaMailSender} so that sent messages can be inspected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.rate-limit.max-requests=10000")
public abstract class IntegrationTestBase {

  protected static final String ORGANIZER_USER = "organizer";
  protected static final String ORGANIZER_PASSWORD = "integration-secret";

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
  static final Path BACKUP_DIR;

  static {
    POSTGRES.start();
    try {
      BACKUP_DIR = Files.createTempDirectory("registration-backups");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("app.backup.directory", BACKUP_DIR::toString);
    registry.add("app.recaptcha.test-mode", () -> "true");
    registry.add("app.organizer.username", () -> ORGANIZER_USER);
    registry.add("app.organizer.password", () -> ORGANIZER_PASSWORD);
    registry.add("app.mail.organizer-recipients", () -> "org1@example.org, org2@example.org");
    registry.add("app.mail.from", () -> "registration@example.org");
  }

  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcTemplate jdbc;
  @MockitoBean protected JavaMailSender mailSender;

  @BeforeEach
  void resetState() throws IOException {
    jdbc.update("DELETE FROM registration");
    try (Stream<Path> files = Files.list(BACKUP_DIR)) {
      for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(file);
      }
    }
    when(mailSender.createMimeMessage())
        .thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
  }

  protected static List<Path> backupFiles() throws IOException {
    try (Stream<Path> files = Files.list(BACKUP_DIR)) {
      return files.filter(p -> p.toString().endsWith(".json")).toList();
    }
  }

  protected int registrationCount() {
    Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM registration", Integer.class);
    return count == null ? 0 : count;
  }
}
