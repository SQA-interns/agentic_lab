package si.konferenca.registration.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for Phase 5 integration tests that need a running application with PostgreSQL 16 but no mail
 * catcher (SMTP points at a closed port; mail failures never affect a registration). Independent of
 * the frozen acceptance harness.
 */
@SpringBootTest(
    classes = si.konferenca.registration.Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class PostgresIntegrationTest {

  protected static final String ORGANIZER_USER = "organizer";
  protected static final String ORGANIZER_PASSWORD = "integration-organizer-password-42";
  protected static final ObjectMapper JSON = new ObjectMapper();
  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");
  protected static final Path OPTIONS_FILE;
  protected static final Path BACKUP_DIR;

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  static {
    POSTGRES.start();
    try {
      Path work = Files.createTempDirectory("registration-it");
      OPTIONS_FILE = work.resolve("options.json");
      BACKUP_DIR = work.resolve("backups");
      Files.writeString(
          OPTIONS_FILE,
          "{\"options\":[{\"id\":\"ws-a\",\"category\":\"WORKSHOP\",\"name\":\"A\",\"active\":true}]}");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @LocalServerPort protected int port;

  /** Common settings; call from a subclass {@code @DynamicPropertySource} and add its own. */
  protected static void registerCommon(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.mail.host", () -> "127.0.0.1");
    registry.add("spring.mail.port", () -> "9");
    registry.add("app.options.file", OPTIONS_FILE::toString);
    registry.add("app.backup.dir", BACKUP_DIR::toString);
    registry.add("app.organizer.username", () -> ORGANIZER_USER);
    registry.add("app.organizer.password", () -> ORGANIZER_PASSWORD);
    registry.add("app.organizer.emails", () -> "org@example.si");
  }

  /** Status and body of an HTTP exchange. */
  protected record Result(int status, String body, HttpResponse<String> raw) {}

  protected Result send(HttpRequest.Builder request) {
    try {
      HttpResponse<String> r =
          HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return new Result(r.statusCode(), r.body(), r);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  protected HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
  }

  protected Result postJson(String path, String json) {
    return send(
        request(path)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)));
  }

  protected static ObjectNode externalRegistration(String email, String token) {
    ObjectNode n = JSON.createObjectNode();
    n.put("type", "EXTERNAL");
    n.put("firstName", "Ana");
    n.put("lastName", "Novak");
    n.put("email", email);
    n.put("organization", "IJS");
    n.putArray("optionIds").add("ws-a");
    n.put("personalDataConsent", true);
    n.put("recaptchaToken", token);
    return n;
  }

  protected static String uniqueEmail() {
    return "it." + UUID.randomUUID().toString().substring(0, 8) + "@example.si";
  }

  protected static int countByEmail(String email) {
    try (Connection c =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PreparedStatement ps =
            c.prepareStatement("select count(*) from registration where email = ?")) {
      ps.setString(1, email);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getInt(1);
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }
}
