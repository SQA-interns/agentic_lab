package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Payloads.external;
import static si.konferenca.registration.acceptance.support.Payloads.student;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.AppRunner;
import si.konferenca.registration.acceptance.support.Json;
import tools.jackson.databind.JsonNode;

/** US-005 Reliable registration storage: database row and raw JSON copy (BR-07, AR-05). */
class Us005StorageAcceptanceTest extends AcceptanceTestBase {

  private static final String CONSENT_TEXT =
      "I agree that the organizers process the personal data I enter in this form to organise"
          + " the conference and my participation in it.";

  @Test
  void AC_005_01_accepted_registration_is_stored_with_fields_options_and_consent() {
    Map<String, Object> body = external();
    Instant before = Instant.now().minusSeconds(5);

    JsonNode accepted = assertAccepted(api.register(body));

    Map<String, Object> row = db.registrationByEmail((String) body.get("email"));
    assertThat(row.get("id").toString()).isEqualTo(accepted.path("id").asString());
    assertThat(row.get("type")).isEqualTo("EXTERNAL");
    assertThat(row.get("first_name")).isEqualTo("Janez");
    assertThat(row.get("last_name")).isEqualTo("Novak");
    assertThat(row.get("email")).isEqualTo(body.get("email"));
    assertThat(row.get("organization")).isEqualTo("Institute of Testing");
    List<Map<String, Object>> options = db.options(row.get("id"));
    assertThat(options)
        .extracting(o -> o.get("option_id"))
        .containsExactly("ws-ai-research", "meal-lunch-day1");
    assertThat(options)
        .extracting(o -> o.get("display_name"))
        .containsExactly("Workshop: AI in research", "Lunch, day 1");
    assertThat(options).extracting(o -> o.get("category")).containsExactly("WORKSHOP", "MEAL");
    List<Map<String, Object>> consents = db.consents(row.get("id"));
    assertThat(consents).hasSize(1);
    assertThat(consents.get(0).get("consent_id")).isEqualTo(AcceptanceEnvironment.CONSENT_ID);
    assertThat(consents.get(0).get("consent_text")).isEqualTo(CONSENT_TEXT);
    Instant givenAt = ((Timestamp) consents.get(0).get("given_at")).toInstant();
    assertThat(givenAt).isAfter(before).isBefore(Instant.now().plusSeconds(5));
  }

  @Test
  void AC_005_02_accepted_registration_has_a_raw_json_copy_exactly_as_accepted()
      throws IOException {
    Map<String, Object> body = student();
    body.put("firstName", "  Ana ");

    JsonNode accepted = assertAccepted(api.register(body));

    List<Path> copyFiles = copies.copies();
    assertThat(copyFiles).hasSize(1);
    String id = accepted.path("id").asString();
    assertThat(copyFiles.get(0).getFileName().toString())
        .matches("\\d{8}T\\d{6}Z_" + id + "\\.json");
    JsonNode copy = Json.read(Files.readAllBytes(copyFiles.get(0)));
    assertThat(copy.path("schemaVersion").asInt()).isEqualTo(1);
    assertThat(copy.path("id").asString()).isEqualTo(id);
    assertThat(copy.path("type").asString()).isEqualTo("STUDENT");
    assertThat(copy.path("registeredAt").asString()).isNotBlank();
    JsonNode participant = copy.path("participant");
    assertThat(participant.path("firstName").asString()).isEqualTo("Ana");
    assertThat(participant.path("lastName").asString()).isEqualTo("Horvat");
    assertThat(participant.path("email").asString()).isEqualTo(body.get("email"));
    assertThat(participant.path("studyInstitution").asString())
        .isEqualTo("University of Ljubljana");
    assertThat(participant.path("studyProgramme").asString()).isEqualTo("Computer Science");
    assertThat(participant.path("studentId").asString()).isEqualTo("63210001");
    assertThat(participant.has("organization")).isFalse();
    assertThat(Json.strings(copy.path("options"), "id"))
        .containsExactly("ws-open-data", "meal-lunch-day2");
    assertThat(Json.strings(copy.path("consents"), "id"))
        .containsExactly(AcceptanceEnvironment.CONSENT_ID);
    assertThat(copy.path("consents").get(0).path("text").asString()).isEqualTo(CONSENT_TEXT);
    assertThat(copy.path("consents").get(0).path("givenAt").asString()).isNotBlank();
  }

  @Test
  void AC_005_03_registration_whose_json_copy_cannot_be_written_is_not_accepted() {
    copies.setWritable(false);

    assertError(api.register(external()), 503, "STORAGE_FAILED");

    copies.setWritable(true);
    assertNothingStored();
    assertThat(mail.messagesAfter(Duration.ofSeconds(2))).isEmpty();
  }

  @Test
  void AC_005_03_registration_whose_database_record_cannot_be_written_is_not_accepted() {
    assertAccepted(api.register(external()));
    copies.clear();
    mail.clear();
    db.blockInserts();

    assertError(api.register(external()), 503, "STORAGE_FAILED");

    db.unblockInserts();
    assertThat(db.countRegistrations()).isEqualTo(1);
    assertThat(copies.files()).isEmpty();
    assertThat(mail.messagesAfter(Duration.ofSeconds(2))).isEmpty();
  }

  @Test
  void AC_005_04_registrations_and_copies_survive_a_restart() {
    Map<String, Object> first = external();
    Map<String, Object> second = student();
    try (AppRunner app = AppRunner.start(AcceptanceEnvironment.baseSettings())) {
      assertAccepted(app.api().register(first));
      assertAccepted(app.api().register(second));
    }

    try (AppRunner app = AppRunner.start(AcceptanceEnvironment.baseSettings())) {
      assertThat(app.api().formConfig().status()).isEqualTo(200);
      assertThat(db.registrationByEmail((String) first.get("email"))).isNotNull();
      assertThat(db.registrationByEmail((String) second.get("email"))).isNotNull();
      assertThat(copies.copies()).hasSize(2);
      assertError(app.api().register(first), 409, "DUPLICATE_EMAIL");
    }
  }

  @Test
  void AC_005_05_slovenian_characters_are_unchanged_in_database_and_json_copy() throws IOException {
    Map<String, Object> body = student();
    body.put("firstName", "Špela");
    body.put("lastName", "Žagar Čuk");
    body.put("studyProgramme", "Računalništvo in informatika");

    assertAccepted(api.register(body));

    Map<String, Object> row = db.registrationByEmail((String) body.get("email"));
    assertThat(row.get("first_name")).isEqualTo("Špela");
    assertThat(row.get("last_name")).isEqualTo("Žagar Čuk");
    assertThat(row.get("study_programme")).isEqualTo("Računalništvo in informatika");
    String raw = Files.readString(copies.copies().get(0), StandardCharsets.UTF_8);
    assertThat(raw).contains("Špela", "Žagar Čuk", "Računalništvo in informatika");
    JsonNode participant = Json.read(raw).path("participant");
    assertThat(participant.path("firstName").asString()).isEqualTo("Špela");
    assertThat(participant.path("lastName").asString()).isEqualTo("Žagar Čuk");
  }
}
