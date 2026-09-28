package org.example.conference.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL 16 (Testcontainers, singleton) + real filesystem backup directory. Only captcha
 * (deterministic test mode) and SMTP (GreenMail where needed) are substituted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

  public static final String POSTGRES_IMAGE =
      "postgres:16@sha256:1a6ab3f5345eb6dbe04a1349529caabdb0ab09293a09590fad07b2246bfa4b54";

  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
          DockerImageName.parse(POSTGRES_IMAGE).asCompatibleSubstituteFor("postgres"));

  protected static final Path BACKUP_DIR;

  static {
    POSTGRES.start();
    try {
      BACKUP_DIR = Files.createTempDirectory("conference-backups-");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Autowired protected MockMvc mockMvc;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected ObjectMapper objectMapper;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("app.backup.directory", BACKUP_DIR::toString);
    registry.add(
        "app.catalog.path",
        () -> Path.of("src/test/resources/catalog-test.yaml").toAbsolutePath().toString());
  }

  @BeforeEach
  void cleanState() throws IOException {
    jdbc.update("DELETE FROM email_outbox");
    jdbc.update("DELETE FROM registration");
    for (String sub : List.of("staging", "registrations", "orphaned")) {
      Path dir = BACKUP_DIR.resolve(sub);
      if (Files.isDirectory(dir)) {
        try (Stream<Path> files = Files.list(dir)) {
          for (Path file : files.toList()) {
            Files.deleteIfExists(file);
          }
        }
      }
    }
  }

  protected ResultActions postJson(String path, Object body) throws Exception {
    return mockMvc.perform(
        post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(body)));
  }

  protected int registrationCount() {
    Integer count = jdbc.queryForObject("SELECT count(*) FROM registration", Integer.class);
    return count == null ? 0 : count;
  }

  protected long publishedFileCount() throws IOException {
    try (Stream<Path> files = Files.list(BACKUP_DIR.resolve("registrations"))) {
      return files.count();
    }
  }
}
