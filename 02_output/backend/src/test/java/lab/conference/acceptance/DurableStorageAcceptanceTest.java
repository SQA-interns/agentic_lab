package lab.conference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import lab.conference.acceptance.support.Api;
import lab.conference.acceptance.support.AppInstance;
import lab.conference.acceptance.support.Infra;
import lab.conference.acceptance.support.Mailpit;
import lab.conference.acceptance.support.Payloads;
import lab.conference.acceptance.support.Store;
import lab.conference.acceptance.support.Workbook;
import org.junit.jupiter.api.Test;

/** US-005: durable storage in the database and raw JSON files. */
class DurableStorageAcceptanceTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void ac_005_01_acceptedRegistrationHasMatchingDatabaseRowAndJsonFile() throws Exception {
    try (AppInstance app = AppInstance.builder().build().start()) {
      Map<String, Object> body = Payloads.student();
      Api.Response r = app.api().postStudent(body);
      assertThat(r.status()).as(r.toString()).isEqualTo(201);
      String id = r.json().path("registrationId").asText();

      Path file = app.store().jsonFile(id);
      assertThat(file).exists();
      byte[] bytes = Files.readAllBytes(file);
      JsonNode record = JSON.readTree(bytes);
      assertThat(record.path("schemaVersion").asInt()).isEqualTo(1);
      assertThat(record.path("registrationId").asText()).isEqualTo(id);
      assertThat(record.path("clientRequestId").asText()).isEqualTo(body.get("clientRequestId"));
      assertThat(record.path("formType").asText()).isEqualTo("student");
      assertThat(record.path("participant").path("email").asText()).isEqualTo(body.get("email"));
      assertThat(record.path("participant").path("studentId").asText()).isEqualTo("S-12345/Č");
      assertThat(record.path("selections").path("workshops").get(0).path("id").asText())
          .isEqualTo("ws-alpha");
      assertThat(record.path("selections").path("workshops").get(0).path("name").asText())
          .isEqualTo("Workshop Alpha – Čebelarstvo");
      assertThat(record.path("consent").path("given").asBoolean()).isTrue();
      assertThat(record.has("captchaToken")).isFalse();
      assertThat(new String(bytes, StandardCharsets.UTF_8)).doesNotContain("local-captcha-ok");

      Map<String, Object> row = app.store().registrationRow(id);
      assertThat(row)
          .containsEntry(
              "client_request_id", UUID.fromString((String) body.get("clientRequestId")));
      assertThat(row).containsEntry("form_type", "student");
      assertThat(row).containsEntry("email", body.get("email"));
      assertThat(row.get("json_sha256"))
          .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
  }

  @Test
  void ac_005_02_unwritableJsonStoreRejectsWithoutStoringAnything() throws Exception {
    try (AppInstance app = AppInstance.builder().build().start()) {
      Store store = app.store();
      Path registrations = store.registrationsDir();
      Path staging = app.jsonDir().resolve("staging");
      Store.Snapshot before = store.snapshot();
      Map<String, Object> body = Payloads.external();
      assertThat(registrations).as("JSON store created at startup").isDirectory();
      assertThat(staging).as("JSON staging area created at startup").isDirectory();
      try {
        Files.setPosixFilePermissions(registrations, PosixFilePermissions.fromString("r-x------"));
        Files.setPosixFilePermissions(staging, PosixFilePermissions.fromString("r-x------"));

        Api.Response r = app.api().postExternal(body);

        assertThat(r.status()).as(r.toString()).isGreaterThanOrEqualTo(500);
        assertThat(r.text()).doesNotContain("Exception", "at lab.", "/registrations");
      } finally {
        Files.setPosixFilePermissions(registrations, PosixFilePermissions.fromString("rwx------"));
        Files.setPosixFilePermissions(staging, PosixFilePermissions.fromString("rwx------"));
      }
      Mailpit.sleep(2000);
      store.assertUnchangedSince(before);
      assertThat(Mailpit.shared().messagesTo((String) body.get("email"))).isEmpty();

      Api.Response retry = app.api().postExternal(body);
      assertThat(retry.status()).as("retry after the store recovers").isEqualTo(201);
    }
  }

  @Test
  void ac_005_04_orphanJsonWithoutDatabaseRowIsQuarantinedAndNotAccepted() throws Exception {
    try (AppInstance app = AppInstance.builder().build().start()) {
      String orphanId = UUID.randomUUID().toString();
      String orphanEmail = Payloads.uniqueEmail("orphan");
      assertThat(app.store().registrationsDir()).as("JSON store created at startup").isDirectory();
      Path orphan = app.store().jsonFile(orphanId);
      Files.writeString(
          orphan,
          "{\"schemaVersion\":1,\"registrationId\":\""
              + orphanId
              + "\",\"clientRequestId\":\""
              + UUID.randomUUID()
              + "\",\"formType\":\"external\",\"acceptedAt\":\"2026-01-01T00:00:00Z\","
              + "\"participant\":{\"firstName\":\"Orphan\",\"lastName\":\"Crash\",\"email\":\""
              + orphanEmail
              + "\",\"organization\":\"Leftover\"},\"selections\":{\"workshops\":[],\"events\":[],"
              + "\"meals\":[],\"other\":[]},\"consent\":{\"id\":\"test-consent\",\"given\":true}}",
          StandardCharsets.UTF_8);
      Files.setLastModifiedTime(orphan, FileTime.from(Instant.now().minus(Duration.ofHours(1))));

      Instant deadline = Instant.now().plusSeconds(30);
      while (Files.exists(orphan) && Instant.now().isBefore(deadline)) {
        Mailpit.sleep(300);
      }

      assertThat(orphan).doesNotExist();
      assertThat(app.store().orphanedFiles())
          .extracting(p -> p.getFileName().toString())
          .contains(orphanId + ".json");
      assertThat(app.store().registrationsWithEmail(orphanEmail)).isZero();
      Api.Response export = app.api().export(app.organizerUser(), app.organizerPassword());
      assertThat(export.status()).isEqualTo(200);
      assertThat(new Workbook(export.bytes()).containsText(orphanEmail)).isFalse();
    }
  }

  @Test
  void ac_005_05_acceptedRegistrationsSurviveBackendAndDatabaseRestart() throws Exception {
    int pgPort = Infra.freePort();
    var postgres = Infra.newPostgresOnFixedPort(pgPort);
    postgres.start();
    try {
      Infra.DatabaseRef db = Infra.newDatabase(postgres);
      AppInstance first = AppInstance.builder().database(db).build().start();
      Path jsonDir = first.jsonDir();
      Api.Response external = first.api().postExternal(Payloads.external());
      Api.Response student = first.api().postStudent(Payloads.student());
      assertThat(external.status()).isEqualTo(201);
      assertThat(student.status()).isEqualTo(201);
      String externalId = external.json().path("registrationId").asText();
      String studentId = student.json().path("registrationId").asText();
      first.stop();

      postgres.getDockerClient().stopContainerCmd(postgres.getContainerId()).exec();
      postgres.getDockerClient().startContainerCmd(postgres.getContainerId()).exec();
      waitForDatabase(db);

      try (AppInstance second =
          AppInstance.builder().database(db).jsonDir(jsonDir).build().start()) {
        assertThat(second.store().jsonFile(externalId)).exists();
        assertThat(second.store().jsonFile(studentId)).exists();
        assertThat(second.store().registrationCount()).isEqualTo(2);
        Api.Response export =
            second.api().export(second.organizerUser(), second.organizerPassword());
        assertThat(export.status()).isEqualTo(200);
        Workbook wb = new Workbook(export.bytes());
        assertThat(wb.row(externalId)).isNotNull();
        assertThat(wb.row(studentId)).isNotNull();
      }
    } finally {
      postgres.stop();
    }
  }

  private static void waitForDatabase(Infra.DatabaseRef db) {
    Instant deadline = Instant.now().plusSeconds(60);
    while (Instant.now().isBefore(deadline)) {
      try (var c = java.sql.DriverManager.getConnection(db.url(), db.username(), db.password())) {
        if (c.isValid(2)) {
          return;
        }
      } catch (java.sql.SQLException e) {
        Mailpit.sleep(500);
      }
    }
    throw new AssertionError("database did not come back");
  }
}
