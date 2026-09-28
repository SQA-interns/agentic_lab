package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/** US-005 — Reliable registration storage (database + raw JSON backup, specification §10). */
class DurableStorageAcceptanceTest extends AcceptanceTestBase {

  @Test
  void ac_005_01_registrationSurvivesAnApplicationRestart() {
    String email = uniqueEmail("ac00501");
    Resp r = register(validStudent(email, "ws-ai", "meal-lunch1"));
    assertThat(r.status()).as(r.text()).isEqualTo(201);
    String id = r.json().path("registrationId").asText();

    // A second, freshly started application instance on the same database and backup storage.
    try (ConfigurableApplicationContext restarted =
        new SpringApplicationBuilder(si.konferenca.registration.Application.class)
            .properties(
                "server.port=0",
                "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "spring.datasource.username=" + POSTGRES.getUsername(),
                "spring.datasource.password=" + POSTGRES.getPassword(),
                "spring.mail.host=" + MAILPIT.getHost(),
                "spring.mail.port=" + MAILPIT.getMappedPort(1025),
                "app.options.file=" + OPTIONS_FILE,
                "app.backup.dir=" + BACKUP_DIR,
                "app.organizer.username=" + ORGANIZER_USER,
                "app.organizer.password=" + ORGANIZER_PASSWORD,
                "app.organizer.emails=" + ORGANIZER_EMAIL,
                "app.recaptcha.test-mode=true")
            .run()) {
      int restartedPort = ((WebServerApplicationContext) restarted).getWebServer().getPort();
      Resp export =
          send(
              HttpRequest.newBuilder(
                      URI.create(
                          "http://localhost:"
                              + restartedPort
                              + "/api/organizer/registrations/export"))
                  .header("Authorization", basic(ORGANIZER_USER, ORGANIZER_PASSWORD))
                  .GET()
                  .build());
      assertThat(export.status()).isEqualTo(200);
      List<String> row =
          ExcelReader.rows(export.body()).stream()
              .filter(x -> x.get(0).equals(id))
              .findFirst()
              .orElseThrow(() -> new AssertionError("registration missing after restart"));
      assertThat(row.get(5)).isEqualTo(email);
      assertThat(row.get(10)).isEqualTo("Delavnica: umetna inteligenca");
      assertThat(row.get(12)).isEqualTo("Kosilo, 1. dan");
    }
  }

  @Test
  void ac_005_01_acceptedRegistrationHasARawJsonBackup() throws IOException {
    String email = uniqueEmail("ac00501b");
    Resp r = register(validExternal(email, "ev-dinner"));
    assertThat(r.status()).as(r.text()).isEqualTo(201);
    String id = r.json().path("registrationId").asText();

    assertThat(backupFileOf(id)).exists();
    JsonNode backup = JSON.readTree(Files.readAllBytes(backupFileOf(id)));
    assertThat(backup.path("schemaVersion").asInt()).isEqualTo(1);
    assertThat(backup.path("registrationId").asText()).isEqualTo(id);
    assertThat(backup.path("type").asText()).isEqualTo("EXTERNAL");
    assertThat(backup.path("email").asText()).isEqualTo(email);
    assertThat(backup.path("organization").asText()).isEqualTo("Institut Jožef Stefan");
    assertThat(backup.path("personalDataConsentAt").asText()).isNotBlank();
    assertThat(backup.path("options").get(0).path("id").asText()).isEqualTo("ev-dinner");
    assertThat(backup.path("options").get(0).path("category").asText()).isEqualTo("EVENT");
    assertThat(backup.path("options").get(0).path("name").asText()).isEqualTo("Conference dinner");
  }

  @Test
  void ac_005_02_registrationAndOptionsAreStoredTogether() {
    String email = uniqueEmail("ac00502");
    Resp r = register(validExternal(email, "ws-ai", "ev-dinner", "other-tour"));

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    assertThat(countRegistrationsByEmail(email)).isEqualTo(1);
    assertThat(optionIdsOf(r.json().path("registrationId").asText()))
        .containsExactly("ev-dinner", "other-tour", "ws-ai");
  }

  @Test
  void ac_005_02_rejectedSubmissionLeavesNoPartialRegistration() throws IOException {
    String email = uniqueEmail("ac00502b");
    long backupsBefore;
    try (var s = Files.list(BACKUP_DIR)) {
      backupsBefore = s.count();
    }
    ObjectNode body = validExternal(email, "ws-ai", "ws-old");

    assertThat(register(body).status()).isEqualTo(400);

    assertThat(countRegistrationsByEmail(email)).isZero();
    try (var s = Files.list(BACKUP_DIR)) {
      assertThat(s.count()).isEqualTo(backupsBefore);
    }
  }

  @Test
  void ac_005_03_registrationsCanBeRestoredFromBackups() throws SQLException {
    String emailA = uniqueEmail("ac00503a");
    String emailB = uniqueEmail("ac00503b");
    Resp a = register(validExternal(emailA, "ws-ai"));
    Resp b = register(validStudent(emailB, "meal-lunch1", "other-tour"));
    assertThat(a.status()).isEqualTo(201);
    assertThat(b.status()).isEqualTo(201);
    String idA = a.json().path("registrationId").asText();
    String idB = b.json().path("registrationId").asText();

    // Simulate loss of the database contents.
    try (Connection c = db();
        PreparedStatement delOptions = c.prepareStatement("delete from registration_option");
        PreparedStatement delRegs = c.prepareStatement("delete from registration")) {
      delOptions.executeUpdate();
      delRegs.executeUpdate();
    }
    assertThat(countRegistrationsByEmail(emailA)).isZero();

    Resp restore = postJsonAsOrganizer("/api/organizer/backups/restore", "{}");

    assertThat(restore.status()).as(restore.text()).isEqualTo(200);
    assertThat(restore.json().path("restored").asInt()).isGreaterThanOrEqualTo(2);
    assertThat(restore.json().path("failed").asInt()).isZero();
    assertThat(registrationRow(idA).get("email")).isEqualTo(emailA);
    assertThat(registrationRow(idB).get("student_id")).isEqualTo("63200001");
    assertThat(optionIdsOf(idB)).containsExactly("meal-lunch1", "other-tour");

    Resp again = postJsonAsOrganizer("/api/organizer/backups/restore", "{}");
    assertThat(again.status()).isEqualTo(200);
    assertThat(again.json().path("restored").asInt()).isZero();
    assertThat(again.json().path("alreadyPresent").asInt()).isGreaterThanOrEqualTo(2);
    assertThat(countRegistrationsByEmail(emailA)).isEqualTo(1);
  }

  @Test
  void ac_005_03_restoreRequiresOrganizerAccess() {
    Resp r = postJson("/api/organizer/backups/restore", "{}");

    assertThat(r.status()).isEqualTo(401);
  }
}
