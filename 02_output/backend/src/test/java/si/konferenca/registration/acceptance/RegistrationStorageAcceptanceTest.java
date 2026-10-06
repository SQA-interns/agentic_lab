package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.student;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.Mailpit;
import si.konferenca.registration.acceptance.support.RunningApp;
import si.konferenca.registration.acceptance.support.RunningApp.Response;
import tools.jackson.databind.JsonNode;

/** US-005 Reliable registration storage: database contract plus raw JSON copy (BR-07, AR-05). */
class RegistrationStorageAcceptanceTest {

  private static List<Path> jsonCopies(Path dir) throws Exception {
    try (Stream<Path> s = Files.list(dir)) {
      return s.filter(p -> p.getFileName().toString().endsWith(".json")).toList();
    }
  }

  private static Path jsonCopyOf(Path dir, String registrationId) throws Exception {
    return jsonCopies(dir).stream()
        .filter(p -> p.getFileName().toString().endsWith("_" + registrationId + ".json"))
        .findFirst()
        .orElse(null);
  }

  @Test
  void AC_005_01_acceptedRegistrationIsStoredWithAllData() {
    try (RunningApp app = RunningApp.start()) {
      Map<String, Object> reg = student();
      Response r = app.register(reg);
      assertThat(r.status()).isEqualTo(201);

      Map<String, Object> row = app.registrationRows((String) reg.get("email")).get(0);
      assertThat(row.get("id").toString()).isEqualTo(r.json().path("registrationId").asString());
      assertThat(row.get("registration_type")).isEqualTo("STUDENT");
      assertThat(row.get("first_name")).isEqualTo("Luka");
      assertThat(row.get("last_name")).isEqualTo("Kranjc");
      assertThat(row.get("email")).isEqualTo(reg.get("email"));
      assertThat(row.get("study_institution")).isEqualTo("Univerza v Mariboru");
      assertThat(row.get("study_programme")).isEqualTo("Informatika");
      assertThat(row.get("student_id")).isEqualTo("93120001");
      assertThat(row.get("consent_id")).isEqualTo("data-processing");
      assertThat((String) row.get("consent_text")).startsWith("I agree to the processing");
      assertThat(row.get("consent_given_at")).isNotNull();
      Instant receivedAt = Instant.parse(r.json().path("receivedAt").asString());
      assertThat(((OffsetDateTime) row.get("received_at")).toInstant())
          .isBetween(receivedAt.minusMillis(1), receivedAt.plusMillis(1));
      List<Map<String, Object>> options = app.optionRows(row.get("id"));
      assertThat(options)
          .extracting(o -> o.get("option_id"))
          .containsExactly("ev-tour", "ws-security");
      assertThat(options)
          .extracting(o -> o.get("option_name"))
          .containsExactly("City tour", "Security workshop");
      assertThat(options)
          .extracting(o -> o.get("option_category"))
          .containsExactly("EVENT", "WORKSHOP");
    }
  }

  @Test
  void AC_005_02_acceptedRegistrationHasRawJsonCopy() throws Exception {
    try (RunningApp app = RunningApp.start()) {
      Map<String, Object> reg = external();
      Response r = app.register(reg);
      assertThat(r.status()).isEqualTo(201);
      String id = r.json().path("registrationId").asString();

      Path copy = jsonCopyOf(app.jsonCopyDir(), id);
      assertThat(copy).isNotNull();
      assertThat(copy.getFileName().toString()).matches("\\d{8}T\\d{6}Z_" + id + "\\.json");
      JsonNode json = RunningApp.JSON.readTree(Files.readAllBytes(copy));
      assertThat(json.path("schemaVersion").asInt()).isEqualTo(1);
      assertThat(json.path("registrationId").asString()).isEqualTo(id);
      assertThat(json.path("receivedAt").asString()).isNotBlank();
      assertThat(Instant.parse(json.path("receivedAt").asString()))
          .isEqualTo(Instant.parse(r.json().path("receivedAt").asString()));
      assertThat(json.path("type").asString()).isEqualTo("EXTERNAL");
      JsonNode p = json.path("participant");
      assertThat(p.path("firstName").asString()).isEqualTo("Ana");
      assertThat(p.path("lastName").asString()).isEqualTo("Novak");
      assertThat(p.path("email").asString()).isEqualTo(reg.get("email"));
      assertThat(p.path("organization").asString()).isEqualTo("Institut Jožef Stefan");
      assertThat(p.has("studentId")).isFalse();
      assertThat(json.path("options").valueStream().map(o -> o.path("id").asString()).toList())
          .containsExactlyInAnyOrder("ws-ai", "meal-lunch");
      assertThat(json.path("consent").path("id").asString()).isEqualTo("data-processing");
      assertThat(json.path("consent").path("givenAt").asString()).isNotBlank();
    }
  }

  @Test
  void AC_005_03_unwritableJsonCopyMeansNotAcceptedAndNothingRemains() throws Exception {
    try (RunningApp app = RunningApp.start()) {
      Path dir = app.jsonCopyDir();
      Map<String, Object> reg = external();
      String email = (String) reg.get("email");
      Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-x------"));
      Response r;
      try {
        r = app.register(reg);
      } finally {
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
      }

      assertThat(r.status()).isEqualTo(503);
      assertThat(r.json().has("registrationId")).isFalse();
      assertThat(app.registrationRows(email)).isEmpty();
      assertThat(jsonCopies(dir)).isEmpty();
      Mailpit mail = AcceptanceEnvironment.mailpit();
      assertThat(mail.noMessageWithin(email, Duration.ofSeconds(3))).isTrue();
      for (String organizer : app.organizerEmails().split(",")) {
        assertThat(mail.messagesTo(organizer)).isEmpty();
      }
    }
  }

  @Test
  void AC_005_04_databaseFailureMeansNotAcceptedAndNoEmail() throws Exception {
    try (PostgreSQLContainer db =
        new PostgreSQLContainer(DockerImageName.parse("postgres:16.15-alpine"))) {
      db.start();
      Map<String, String> config = AcceptanceEnvironment.defaultConfiguration();
      config.put("DB_URL", db.getJdbcUrl());
      config.put("DB_USERNAME", db.getUsername());
      config.put("POSTGRES_PASSWORD", db.getPassword());
      config.put("spring.datasource.url", db.getJdbcUrl());
      config.put("spring.datasource.username", db.getUsername());
      config.put("spring.datasource.password", db.getPassword());
      try (RunningApp app = RunningApp.start(config)) {
        db.stop();
        Map<String, Object> reg = external();
        String email = (String) reg.get("email");

        Response r = app.register(reg);

        assertThat(r.status()).isEqualTo(503);
        assertThat(r.json().has("registrationId")).isFalse();
        assertThat(jsonCopies(app.jsonCopyDir())).isEmpty();
        Mailpit mail = AcceptanceEnvironment.mailpit();
        assertThat(mail.noMessageWithin(email, Duration.ofSeconds(3))).isTrue();
        for (String organizer : app.organizerEmails().split(",")) {
          assertThat(mail.messagesTo(organizer)).isEmpty();
        }
      }
    }
  }

  @Test
  void AC_005_05_registrationsAndCopiesSurviveRestart() throws Exception {
    Map<String, String> config;
    String id;
    String email;
    try (RunningApp app = RunningApp.start()) {
      config = app.config();
      Map<String, Object> reg = external();
      email = (String) reg.get("email");
      Response r = app.register(reg);
      assertThat(r.status()).isEqualTo(201);
      id = r.json().path("registrationId").asString();
    }

    try (RunningApp restarted = RunningApp.start(config)) {
      assertThat(restarted.registrationRows(email)).hasSize(1);
      assertThat(jsonCopyOf(restarted.jsonCopyDir(), id)).isNotNull();
      Response export = restarted.exportAsOrganizer();
      assertThat(export.status()).isEqualTo(200);
    }
  }
}
