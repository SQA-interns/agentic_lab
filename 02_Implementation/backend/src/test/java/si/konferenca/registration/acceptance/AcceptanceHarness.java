package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Shared black-box harness for the acceptance suite (Phase 3, frozen).
 *
 * <p>Drives the real HTTP API described in docs/contracts/openapi.yaml against an in-process
 * application on a random port, with PostgreSQL 16 and a Mailpit SMTP catcher in containers.
 * Configuration uses only the properties named in docs/specification.md §9. Database checks use
 * only the tables named in docs/specification.md §3.
 */
@SpringBootTest(
    classes = si.konferenca.registration.Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AcceptanceHarness {

  protected static final String ORGANIZER_USER = "organizer";
  protected static final String ORGANIZER_PASSWORD = "acceptance-organizer-password-0123";
  protected static final String ORGANIZER_EMAIL = "organizers@conference.example";
  protected static final String TOKEN_PASS = "test-pass";

  protected static final ObjectMapper JSON = new ObjectMapper();
  protected static final HttpClient HTTP = HttpClient.newHttpClient();

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static final GenericContainer<?> MAILPIT =
      new GenericContainer<>("axllent/mailpit:v1.31.1")
          .withExposedPorts(1025, 8025)
          .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025));

  static final Path WORK_DIR;
  protected static final Path OPTIONS_FILE;
  protected static final Path BACKUP_DIR;

  static {
    POSTGRES.start();
    MAILPIT.start();
    try {
      WORK_DIR = Files.createTempDirectory("registration-acceptance");
      OPTIONS_FILE = WORK_DIR.resolve("conference-options.json");
      BACKUP_DIR = WORK_DIR.resolve("backups");
      Files.createDirectories(BACKUP_DIR);
      writeOptionsFile(defaultOptions());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Registers the harness configuration. {@code overrides} replace individual values; a test class
   * that needs different values extends this class directly and declares its own
   * {@code @DynamicPropertySource} calling this method (a superclass source would otherwise win).
   */
  protected static void registerProperties(
      DynamicPropertyRegistry target, Map<String, Supplier<Object>> overrides) {
    DynamicPropertyRegistry registry =
        (name, supplier) -> target.add(name, overrides.getOrDefault(name, supplier));
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.mail.host", MAILPIT::getHost);
    registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    registry.add("app.options.file", OPTIONS_FILE::toString);
    registry.add("app.backup.dir", BACKUP_DIR::toString);
    registry.add("app.organizer.username", () -> ORGANIZER_USER);
    registry.add("app.organizer.password", () -> ORGANIZER_PASSWORD);
    registry.add("app.organizer.emails", () -> ORGANIZER_EMAIL);
    registry.add("app.mail.from", () -> "registration@conference.example");
    registry.add("app.conference-name", () -> "Acceptance Conference");
    registry.add("app.recaptcha.test-mode", () -> "true");
    registry.add("app.rate-limit.registration-per-minute", () -> "100000");
    registry.add("app.rate-limit.organizer-per-minute", () -> "100000");
  }

  @LocalServerPort protected int port;

  @BeforeEach
  void resetOptionsToDefault() {
    writeOptionsFile(defaultOptions());
  }

  // ---------------------------------------------------------------- options file

  /** One option entry as in docs/contracts/conference-options.schema.json. */
  protected record Opt(String id, String category, String name, boolean active) {}

  protected static List<Opt> defaultOptions() {
    return List.of(
        new Opt("ws-ai", "WORKSHOP", "Delavnica: umetna inteligenca", true),
        new Opt("ws-sec", "WORKSHOP", "Varnost spletnih aplikacij", true),
        new Opt("ws-old", "WORKSHOP", "Retired workshop", false),
        new Opt("ev-dinner", "EVENT", "Conference dinner", true),
        new Opt("meal-lunch1", "MEAL", "Kosilo, 1. dan", true),
        new Opt("meal-veg", "MEAL", "Vegetarian lunch", false),
        new Opt("other-tour", "OTHER", "Ogled Ljubljane (city tour)", true));
  }

  /**
   * Writes the options file and moves its last-modified time forward so the change is detected
   * (specification §4: re-sync when last-modified time or size changes).
   */
  protected static void writeOptionsFile(List<Opt> options) {
    ObjectNode root = JSON.createObjectNode();
    ArrayNode arr = root.putArray("options");
    for (Opt o : options) {
      arr.addObject()
          .put("id", o.id())
          .put("category", o.category())
          .put("name", o.name())
          .put("active", o.active());
    }
    try {
      FileTime previous =
          Files.exists(OPTIONS_FILE) ? Files.getLastModifiedTime(OPTIONS_FILE) : null;
      Files.writeString(OPTIONS_FILE, JSON.writeValueAsString(root), StandardCharsets.UTF_8);
      Instant next = Instant.now().plusSeconds(2);
      if (previous != null && !next.isAfter(previous.toInstant())) {
        next = previous.toInstant().plusSeconds(2);
      }
      Files.setLastModifiedTime(OPTIONS_FILE, FileTime.from(next));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  protected static List<Opt> withChanged(List<Opt> base, Opt replacementOrAddition) {
    List<Opt> out = new ArrayList<>();
    boolean replaced = false;
    for (Opt o : base) {
      if (o.id().equals(replacementOrAddition.id())) {
        out.add(replacementOrAddition);
        replaced = true;
      } else {
        out.add(o);
      }
    }
    if (!replaced) {
      out.add(replacementOrAddition);
    }
    return out;
  }

  // ---------------------------------------------------------------- HTTP

  protected record Resp(int status, Map<String, List<String>> headers, byte[] body) {
    String text() {
      return new String(body, StandardCharsets.UTF_8);
    }

    JsonNode json() {
      try {
        return JSON.readTree(body);
      } catch (IOException e) {
        throw new AssertionError("Response is not JSON (status " + status + "): " + text(), e);
      }
    }

    String header(String name) {
      return headers.entrySet().stream()
          .filter(e -> e.getKey().equalsIgnoreCase(name))
          .flatMap(e -> e.getValue().stream())
          .findFirst()
          .orElse(null);
    }
  }

  protected String url(String path) {
    return "http://localhost:" + port + path;
  }

  protected Resp get(String path) {
    return send(HttpRequest.newBuilder(URI.create(url(path))).GET().build());
  }

  protected Resp getAsOrganizer(String path, String user, String password) {
    return send(
        HttpRequest.newBuilder(URI.create(url(path)))
            .header("Authorization", basic(user, password))
            .GET()
            .build());
  }

  protected Resp postJson(String path, String body) {
    return send(
        HttpRequest.newBuilder(URI.create(url(path)))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build());
  }

  protected Resp postJsonAsOrganizer(String path, String body) {
    return send(
        HttpRequest.newBuilder(URI.create(url(path)))
            .header("Content-Type", "application/json")
            .header("Authorization", basic(ORGANIZER_USER, ORGANIZER_PASSWORD))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build());
  }

  protected Resp register(ObjectNode body) {
    return postJson("/api/registrations", body.toString());
  }

  protected Resp exportAsOrganizer() {
    return getAsOrganizer(
        "/api/organizer/registrations/export", ORGANIZER_USER, ORGANIZER_PASSWORD);
  }

  protected static String basic(String user, String password) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  protected static Resp send(HttpRequest request) {
    try {
      HttpResponse<byte[]> r = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
      return new Resp(r.statusCode(), r.headers().map(), r.body());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  // ---------------------------------------------------------------- request builders

  /** Unique email so every test can find its own rows and mails. */
  protected static String uniqueEmail(String prefix) {
    return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@example.si";
  }

  protected static ObjectNode validExternal(String email, String... optionIds) {
    ObjectNode n = JSON.createObjectNode();
    n.put("type", "EXTERNAL");
    n.put("firstName", "Ana");
    n.put("lastName", "Novak");
    n.put("email", email);
    n.put("organization", "Institut Jožef Stefan");
    ArrayNode ids = n.putArray("optionIds");
    for (String id : optionIds) {
      ids.add(id);
    }
    n.put("personalDataConsent", true);
    n.put("recaptchaToken", TOKEN_PASS);
    return n;
  }

  protected static ObjectNode validStudent(String email, String... optionIds) {
    ObjectNode n = JSON.createObjectNode();
    n.put("type", "STUDENT");
    n.put("firstName", "Luka");
    n.put("lastName", "Šinkovec");
    n.put("email", email);
    n.put("studyInstitution", "Fakulteta za računalništvo in informatiko");
    n.put("studyProgramme", "Računalništvo in informatika");
    n.put("studentId", "63200001");
    ArrayNode ids = n.putArray("optionIds");
    for (String id : optionIds) {
      ids.add(id);
    }
    n.put("personalDataConsent", true);
    n.put("recaptchaToken", TOKEN_PASS);
    return n;
  }

  // ---------------------------------------------------------------- assertions

  protected static void assertRejectedWithFieldError(Resp r, String field, String code) {
    assertThat(r.status()).as("status for %s/%s: %s", field, code, r.text()).isEqualTo(400);
    assertThat(r.header("Content-Type")).contains("application/problem+json");
    JsonNode errors = r.json().path("errors");
    assertThat(errors.isArray()).as("errors[] present: %s", r.text()).isTrue();
    boolean found = false;
    for (JsonNode e : errors) {
      if (field.equals(e.path("field").asText()) && code.equals(e.path("code").asText())) {
        found = true;
        assertThat(e.path("message").asText()).isNotBlank();
      }
    }
    assertThat(found).as("error {field=%s, code=%s} in %s", field, code, r.text()).isTrue();
  }

  // ---------------------------------------------------------------- database (spec §3)

  protected static Connection db() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  protected static int countRegistrationsByEmail(String email) {
    try (Connection c = db();
        PreparedStatement ps =
            c.prepareStatement("select count(*) from registration where email = ?")) {
      ps.setString(1, email);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getInt(1);
      }
    } catch (SQLException e) {
      throw new AssertionError("registration table not queryable", e);
    }
  }

  protected static Map<String, String> registrationRow(String id) {
    try (Connection c = db();
        PreparedStatement ps =
            c.prepareStatement(
                "select type, first_name, last_name, email, organization, study_institution,"
                    + " study_programme, student_id from registration where id = ?::uuid")) {
      ps.setString(1, id);
      try (ResultSet rs = ps.executeQuery()) {
        assertThat(rs.next()).as("registration %s stored", id).isTrue();
        Map<String, String> row = new java.util.HashMap<>();
        for (String col :
            List.of(
                "type",
                "first_name",
                "last_name",
                "email",
                "organization",
                "study_institution",
                "study_programme",
                "student_id")) {
          row.put(col, rs.getString(col));
        }
        return row;
      }
    } catch (SQLException e) {
      throw new AssertionError("registration table not queryable", e);
    }
  }

  protected static List<String> optionIdsOf(String registrationId) {
    try (Connection c = db();
        PreparedStatement ps =
            c.prepareStatement(
                "select option_id from registration_option where registration_id = ?::uuid"
                    + " order by option_id")) {
      ps.setString(1, registrationId);
      List<String> out = new ArrayList<>();
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(rs.getString(1));
        }
      }
      return out;
    } catch (SQLException e) {
      throw new AssertionError("registration_option table not queryable", e);
    }
  }

  protected static Path backupFileOf(String registrationId) {
    return BACKUP_DIR.resolve(registrationId + ".json");
  }
}
