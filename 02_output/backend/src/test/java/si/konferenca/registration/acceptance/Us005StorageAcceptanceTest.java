package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.student;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceStack;
import si.konferenca.registration.acceptance.support.Db;
import si.konferenca.registration.acceptance.support.Json;
import si.konferenca.registration.acceptance.support.Registrations;
import si.konferenca.registration.acceptance.support.Response;
import si.konferenca.registration.acceptance.support.RunningApp;
import si.konferenca.registration.acceptance.support.Storage;
import tools.jackson.databind.JsonNode;

/** US-005 Reliable registration storage: database row plus raw JSON copy. */
class Us005StorageAcceptanceTest {

  @Test
  @DisplayName("AC-005-01 an accepted registration is stored with fields, options and consents")
  void ac005_01_acceptedRegistrationIsStoredInDatabase() {
    Map<String, Object> request = external();

    Response response = AcceptanceStack.shared().api().register(request);

    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    UUID id = UUID.fromString(response.json().path("id").asString());
    Db db = AcceptanceStack.db();
    assertThat(db.registrationById(id).orElseThrow())
        .containsEntry("type", "EXTERNAL")
        .containsEntry("first_name", "Ana")
        .containsEntry("last_name", "Novak")
        .containsEntry("email", request.get("email"))
        .containsEntry("organization", "Institut Primer");
    assertThat(db.optionsOf(id))
        .extracting(r -> r.get("option_id"))
        .containsExactly("ev-reception", "meal-dinner", "ws-testing");
    assertThat(db.optionsOf(id))
        .extracting(r -> r.get("option_name"))
        .contains("Welcome reception", "Conference dinner (vegetarian option)");
    List<Map<String, Object>> consents = db.consentsOf(id);
    assertThat(consents).hasSize(1);
    assertThat(consents.get(0)).containsEntry("consent_id", "data-processing");
    assertThat(consents.get(0).get("given_at")).isNotNull();
    assertThat((String) consents.get(0).get("consent_text"))
        .startsWith("I agree that my personal data is processed");
  }

  @Test
  @DisplayName("AC-005-02 an accepted registration has a raw JSON copy exactly as accepted")
  void ac005_02_acceptedRegistrationHasRawJsonCopy() throws Exception {
    Map<String, Object> request = student();

    Response response = AcceptanceStack.shared().api().register(request);

    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    String id = response.json().path("id").asString();
    Path file = AcceptanceStack.jsonCopyDir().resolve(id + ".json");
    assertThat(file).exists();
    String raw = Files.readString(file, StandardCharsets.UTF_8);
    JsonNode copy = Json.read(raw);
    assertThat(copy.path("schemaVersion").asInt()).isEqualTo(1);
    assertThat(copy.path("id").asString()).isEqualTo(id);
    assertThat(copy.path("receivedAt").asString())
        .isEqualTo(response.json().path("receivedAt").asString());
    assertThat(copy.path("type").asString()).isEqualTo("STUDENT");
    JsonNode participant = copy.path("participant");
    assertThat(participant.path("firstName").asString()).isEqualTo("Luka");
    assertThat(participant.path("lastName").asString()).isEqualTo("Horvat");
    assertThat(participant.path("email").asString()).isEqualTo(request.get("email"));
    assertThat(participant.path("studyInstitution").asString()).isEqualTo("Univerza v Mariboru");
    assertThat(participant.path("studyProgramme").asString()).isEqualTo("Informatika");
    assertThat(participant.path("studentId").asString()).isEqualTo("E1234567");
    assertThat(participant.has("organization")).isFalse();
    assertThat(copy.path("options").size()).isEqualTo(3);
    assertThat(copy.path("consents").get(0).path("id").asString()).isEqualTo("data-processing");
    assertThat(copy.path("consents").get(0).path("givenAt").asString()).isNotBlank();
    assertThat(raw).doesNotContain("recaptcha").doesNotContain("valid-token");
  }

  @Test
  @DisplayName("AC-005-03 when the JSON copy cannot be written the registration is not accepted")
  void ac005_03_failedCopyWriteRejectsRegistrationWithoutPartialRecord() throws Exception {
    Path copies = AcceptanceStack.newTempDir("broken-copies");
    Map<String, Object> request = external();

    try (RunningApp app = AcceptanceStack.start(Map.of("JSON_COPY_DIR", copies.toString()))) {
      Files.delete(copies);
      Files.writeString(copies, "this is a file, not a directory");
      long rowsBefore = AcceptanceStack.db().countRegistrations();

      Response response = app.api().register(request);

      assertThat(response.status()).as(response.toString()).isEqualTo(500);
      assertThat(response.contentType()).startsWith("application/problem+json");
      assertThat(response.text()).doesNotContain("Exception").doesNotContain(copies.toString());
      assertThat(AcceptanceStack.db().registrationByEmail((String) request.get("email"))).isEmpty();
      assertThat(AcceptanceStack.db().countRegistrations()).isEqualTo(rowsBefore);
      assertThat(Files.readString(copies)).isEqualTo("this is a file, not a directory");
    }
  }

  @Test
  @DisplayName("AC-005-04 registrations and their JSON copies survive a restart")
  void ac005_04_registrationsSurviveRestart() {
    Path copies = AcceptanceStack.newTempDir("restart-copies");
    Map<String, Object> request = external();
    String id;
    try (RunningApp first = AcceptanceStack.start(Map.of("JSON_COPY_DIR", copies.toString()))) {
      Response response = first.api().register(request);
      assertThat(response.status()).as(response.toString()).isEqualTo(201);
      id = response.json().path("id").asString();
    }

    try (RunningApp second = AcceptanceStack.start(Map.of("JSON_COPY_DIR", copies.toString()))) {
      Response export = second.api().exportAsOrganizer();
      assertThat(export.status()).as(export.toString()).isEqualTo(200);
      assertThat(ExportReader.rowsByEmail(export.body()))
          .containsKey((String) request.get("email"));
    }
    assertThat(AcceptanceStack.db().registrationById(UUID.fromString(id))).isPresent();
    assertThat(copies.resolve(id + ".json")).exists();
  }

  @Test
  @DisplayName(
      "AC-005-05 registrations older than the retention period are deleted with their copy")
  void ac005_05_expiredRegistrationsAreDeleted() throws Exception {
    AcceptanceStack.shared();
    Path copies = AcceptanceStack.newTempDir("retention-copies");
    UUID expired = UUID.randomUUID();
    UUID recent = UUID.randomUUID();
    Db db = AcceptanceStack.db();
    db.insertExternal(
        expired, Registrations.uniqueEmail("old"), Instant.now().minus(400, ChronoUnit.DAYS));
    db.insertExternal(
        recent, Registrations.uniqueEmail("new"), Instant.now().minus(10, ChronoUnit.DAYS));
    Files.writeString(copies.resolve(expired + ".json"), "{}");
    Files.writeString(copies.resolve(recent + ".json"), "{}");

    try (RunningApp app =
        AcceptanceStack.start(
            Map.of("JSON_COPY_DIR", copies.toString(), "RETENTION_DAYS", "365"))) {
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                assertThat(db.registrationById(expired)).isEmpty();
                assertThat(copies.resolve(expired + ".json")).doesNotExist();
              });
    }
    assertThat(db.registrationById(recent)).isPresent();
    assertThat(copies.resolve(recent + ".json")).exists();
    assertThat(Storage.jsonCopyCount(copies)).isEqualTo(1);
  }
}
