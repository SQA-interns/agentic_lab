package si.konferenca.registration.acceptance;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.ApiClient;
import si.konferenca.registration.acceptance.support.JsonCopies;
import si.konferenca.registration.acceptance.support.Registrations;
import si.konferenca.registration.acceptance.support.RunningApp;
import si.konferenca.registration.acceptance.support.Workbook;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** US-005 Reliable registration storage. */
class Us005StorageAcceptanceTest extends AcceptanceTestBase {

  private static final String CONSENT_TEXT =
      "I agree that the organizers process my personal data given in this form to organise my"
          + " attendance at the conference and the activities I selected.";

  @Test
  @DisplayName("AC-005-01 the database holds every field, the options, the consent and the time")
  void ac005_01_databaseHoldsTheWholeRegistration() {
    ObjectNode registration =
        Registrations.withOptions(
            Registrations.student(), "ws-ai", "meal-dinner", "other-certificate");
    JsonNode accepted = registerAccepted(registration);
    UUID id = idOf(accepted);

    Map<String, Object> row =
        database.registrationByEmail(Registrations.email(registration)).orElseThrow();
    assertThat(row.get("id")).isEqualTo(id);
    assertThat(row.get("type")).isEqualTo("STUDENT");
    assertThat(row.get("first_name")).isEqualTo("Luka");
    assertThat(row.get("last_name")).isEqualTo("Kranjc");
    assertThat(row.get("email")).isEqualTo(Registrations.email(registration));
    assertThat(row.get("study_institution")).isEqualTo("Univerza v Ljubljani");
    assertThat(row.get("study_programme")).isEqualTo("Računalništvo in informatika");
    assertThat(row.get("student_id")).isEqualTo("63210042");
    assertThat(row.get("consent_id")).isEqualTo("personal-data-v1");
    assertThat(row.get("consent_text")).isEqualTo(CONSENT_TEXT);
    Instant acceptedAt = Instant.parse(accepted.get("acceptedAt").asString()).truncatedTo(MILLIS);
    assertThat(((java.sql.Timestamp) row.get("accepted_at")).toInstant().truncatedTo(MILLIS))
        .isEqualTo(acceptedAt);
    assertThat(((java.sql.Timestamp) row.get("consent_given_at")).toInstant().truncatedTo(MILLIS))
        .isEqualTo(acceptedAt);
    List<Map<String, Object>> options = database.options(id);
    assertThat(options)
        .extracting(o -> o.get("option_id"))
        .containsExactly("ws-ai", "meal-dinner", "other-certificate");
    assertThat(options)
        .extracting(o -> o.get("option_name"))
        .containsExactly(
            "Delavnica: umetna inteligenca v praksi",
            "Večerja za udeležence",
            "Potrdilo o udeležbi");
    assertThat(options)
        .extracting(o -> o.get("option_category"))
        .containsExactly("workshop", "meal", "other");
  }

  @Test
  @DisplayName("AC-005-02 a raw JSON copy of the registration as accepted is written")
  void ac005_02_jsonCopyIsWritten() {
    ObjectNode registration = Registrations.withOptions(Registrations.external(), "ev-reception");
    registration.put("firstName", " Špela ");
    JsonNode accepted = registerAccepted(registration);
    UUID id = idOf(accepted);

    Path file = jsonCopies.fileFor(id).orElseThrow();
    assertThat(file.getFileName().toString()).matches("\\d{8}T\\d{9}Z_" + id + "\\.json");
    JsonNode copy = ApiClient.JSON.readTree(jsonCopies.bytes(file));
    assertThat(copy.get("schemaVersion").asInt()).isEqualTo(1);
    assertThat(copy.get("id").asString()).isEqualTo(id.toString());
    assertThat(copy.get("type").asString()).isEqualTo("EXTERNAL");
    assertThat(Instant.parse(copy.get("acceptedAt").asString()))
        .isEqualTo(Instant.parse(accepted.get("acceptedAt").asString()));
    JsonNode participant = copy.get("participant");
    assertThat(participant.get("firstName").asString()).isEqualTo("Špela");
    assertThat(participant.get("lastName").asString()).isEqualTo("Novak");
    assertThat(participant.get("email").asString()).isEqualTo(Registrations.email(registration));
    assertThat(participant.get("organization").asString()).isEqualTo("Univerza v Mariboru");
    assertThat(participant.has("studentId")).isFalse();
    assertThat(copy.get("options")).hasSize(1);
    assertThat(copy.get("options").get(0).get("id").asString()).isEqualTo("ev-reception");
    assertThat(copy.get("options").get(0).get("name").asString()).isEqualTo("Sprejem dobrodošlice");
    assertThat(copy.get("options").get(0).get("category").asString()).isEqualTo("event");
    assertThat(copy.get("consent").get("id").asString()).isEqualTo("personal-data-v1");
    assertThat(copy.get("consent").get("text").asString()).isEqualTo(CONSENT_TEXT);
    assertThat(copy.get("consent").get("givenAt").asString()).isNotBlank();
  }

  @Test
  @DisplayName("AC-005-03 if the JSON copy cannot be written nothing is kept and a retry works")
  void ac005_03_jsonCopyFailureKeepsNothingAndRetryWorks() {
    ObjectNode registration = Registrations.external();
    String email = Registrations.email(registration);
    ApiClient.Response failed;
    jsonCopies.breakStorage();
    try {
      failed = api.register(registration);
    } finally {
      jsonCopies.restoreStorage();
    }

    assertThat(failed.status()).as(failed.text()).isEqualTo(503);
    assertNothingStoredFor(email);
    UUID id = idOf(registerAccepted(registration));
    assertThat(database.countByEmail(email)).isEqualTo(1);
    assertThat(jsonCopies.fileFor(id)).isPresent();
  }

  @Test
  @DisplayName("AC-005-03 if the database row cannot be written nothing is kept and a retry works")
  void ac005_03_databaseFailureKeepsNothingAndRetryWorks() {
    ObjectNode registration = Registrations.student();
    String email = Registrations.email(registration);
    int copiesBefore = jsonCopies.files().size();
    ApiClient.Response failed;
    assertThat(api.formConfig().status()).as("application serves the form").isEqualTo(200);
    database.failInserts(true);
    try {
      failed = api.register(registration);
    } finally {
      database.failInserts(false);
    }

    assertThat(failed.status()).as(failed.text()).isEqualTo(503);
    assertThat(failed.json().get("code").asString()).isEqualTo("STORAGE_UNAVAILABLE");
    assertNothingStoredFor(email);
    assertThat(jsonCopies.files()).hasSize(copiesBefore);
    UUID id = idOf(registerAccepted(registration));
    assertThat(jsonCopies.fileFor(id)).isPresent();
  }

  @Test
  @DisplayName("AC-005-04 accepted registrations and their JSON copies survive a restart")
  void ac005_04_registrationSurvivesRestart() throws Exception {
    Path jsonDir = Files.createTempDirectory("json-ac005-04");
    JsonCopies copies = new JsonCopies(jsonDir);
    var properties =
        AcceptanceEnvironment.propertiesFor(
            AcceptanceEnvironment.jdbcUrl(), jsonDir, AcceptanceEnvironment.OPTIONS_FILE);
    ObjectNode registration = Registrations.external();
    String email = Registrations.email(registration);
    UUID id;
    byte[] copyBefore;
    try (RunningApp first = RunningApp.start(properties)) {
      ApiClient.Response response = first.api().register(registration);
      assertThat(response.status()).as(response.text()).isEqualTo(201);
      id = idOf(response.json());
      copyBefore = copies.bytes(copies.fileFor(id).orElseThrow());
    }

    try (RunningApp second = RunningApp.start(properties)) {
      ApiClient.Response export = second.api().export(second.api().organizerToken());
      assertThat(export.status()).isEqualTo(200);
      assertThat(Workbook.parse(export.body()).rowFor(email)).isPresent();
    }
    assertThat(database.registrationByEmail(email)).isPresent();
    assertThat(new String(copies.bytes(copies.fileFor(id).orElseThrow()), UTF_8))
        .isEqualTo(new String(copyBefore, UTF_8));
  }
}
