package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.CONSENT;
import static si.konferenca.registration.acceptance.support.Registrations.CONSENT_TEXT;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.options;
import static si.konferenca.registration.acceptance.support.Registrations.student;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.ApiClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** US-005 Reliable registration storage: database row and raw JSON copy. */
class RegistrationStorageAcceptanceTest extends AcceptanceTest {

  @Test
  void ac_005_01_databaseHoldsEveryFieldOptionsConsentsAndRegistrationTime() {
    ObjectNode request = options(student(), "ws-ai", "meal-lunch-1", "meal-lunch-2");

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    Instant registeredAt = Instant.parse(response.json().path("registeredAt").asString());
    Map<String, Object> row =
        db.registrationByEmail(request.path("email").asString()).orElseThrow();
    assertThat(row.get("study_institution")).isEqualTo(request.path("studyInstitution").asString());
    assertThat(row.get("study_programme")).isEqualTo(request.path("studyProgramme").asString());
    assertThat(row.get("student_id")).isEqualTo(request.path("studentId").asString());
    assertThat(instant(row.get("registered_at"))).isEqualTo(registeredAt);
    assertThat(db.options(row.get("id")))
        .containsExactly(
            Map.of("option_id", "meal-lunch-1", "option_name", "Lunch, day 1", "category", "meal"),
            Map.of("option_id", "meal-lunch-2", "option_name", "Lunch, day 2", "category", "meal"),
            Map.of(
                "option_id",
                "ws-ai",
                "option_name",
                "Delavnica umetne inteligence",
                "category",
                "workshop"));
    List<Map<String, Object>> consents = db.consents(row.get("id"));
    assertThat(consents).hasSize(1);
    assertThat(consents.get(0).get("consent_id")).isEqualTo(CONSENT);
    assertThat(consents.get(0).get("consent_text")).isEqualTo(CONSENT_TEXT);
    assertThat(instant(consents.get(0).get("given_at"))).isEqualTo(registeredAt);
  }

  @Test
  void ac_005_02_rawJsonCopyOfTheAcceptedRegistrationIsWrittenAsItsOwnFile() {
    ObjectNode request = options(external(), "ws-security", "ev-industry-dinner");
    request.put("firstName", "  Mojca ");

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    String id = response.json().path("registrationId").asString();
    assertThat(copies.fileNames()).containsExactly("registration-" + id + ".json");
    JsonNode copy = copies.jsonOf(id);
    assertThat(fieldNames(copy))
        .containsExactlyInAnyOrder(
            "registrationId", "registeredAt", "type", "participant", "options", "consents");
    assertThat(copy.path("registrationId").asString()).isEqualTo(id);
    assertThat(Instant.parse(copy.path("registeredAt").asString()))
        .isEqualTo(Instant.parse(response.json().path("registeredAt").asString()));
    assertThat(copy.path("type").asString()).isEqualTo("EXTERNAL");
    JsonNode participant = copy.path("participant");
    assertThat(fieldNames(participant))
        .containsExactlyInAnyOrder("firstName", "lastName", "email", "organization");
    assertThat(participant.path("firstName").asString()).isEqualTo("Mojca");
    assertThat(participant.path("lastName").asString()).isEqualTo("Novak");
    assertThat(participant.path("email").asString()).isEqualTo(request.path("email").asString());
    assertThat(participant.path("organization").asString()).isEqualTo("Institut Jožef Stefan");
    List<String> options = new ArrayList<>();
    copy.path("options")
        .forEach(
            o ->
                options.add(
                    o.path("id").asString()
                        + "|"
                        + o.path("name").asString()
                        + "|"
                        + o.path("category").asString()));
    assertThat(options)
        .containsExactlyInAnyOrder(
            "ws-security|Varnost spletnih aplikacij|workshop",
            "ev-industry-dinner|Industry dinner|event");
    JsonNode consent = copy.path("consents").get(0);
    assertThat(consent.path("id").asString()).isEqualTo(CONSENT);
    assertThat(consent.path("text").asString()).isEqualTo(CONSENT_TEXT);
    assertThat(copies.jsonOf(id).toString()).doesNotContain("captcha").doesNotContain("test-valid");
  }

  @Test
  void ac_005_03_registrationIsNotAcceptedWhenTheJsonCopyCannotBeWritten() {
    copies.makeUnwritable();
    try {
      ObjectNode request = external();

      ApiClient.Response response = api.register(request);

      assertThat(response.status()).as(response.text()).isEqualTo(500);
      assertThat(db.countRegistrations()).isZero();
      assertNoEmail();
    } finally {
      copies.clear();
    }
  }

  @Test
  void ac_004_03_storageFailureGivesAGeneralErrorWithoutInternalDetails() {
    copies.makeUnwritable();
    try {
      ApiClient.Response response = api.register(external());

      assertThat(response.status()).as(response.text()).isEqualTo(500);
      JsonNode body = response.json();
      assertThat(body.has("registrationId")).isFalse();
      assertThat(body.path("error").asString()).isEqualTo("internal_error");
      assertThat(body.path("message").asString()).isNotBlank();
      assertThat(response.text())
          .doesNotContainIgnoringCase("exception")
          .doesNotContain("java.")
          .doesNotContain("org.")
          .doesNotContain(copies.dir().getFileName().toString())
          .doesNotContainIgnoringCase("sql")
          .doesNotContain("at si.");
    } finally {
      copies.clear();
    }
  }

  @Test
  void ac_005_05_rejectedRegistrationLeavesNeitherRowNorCopy() {
    ObjectNode request = options(external(), "ws-legacy");

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(400);
    assertThat(db.registrationByEmail(request.path("email").asString())).isEmpty();
    assertThat(copies.fileNames()).isEmpty();
  }

  private static Instant instant(Object databaseValue) {
    if (databaseValue instanceof Timestamp timestamp) {
      return timestamp.toInstant();
    }
    if (databaseValue instanceof java.time.OffsetDateTime offsetDateTime) {
      return offsetDateTime.toInstant();
    }
    throw new AssertionError("not a timestamp: " + databaseValue);
  }

  private static Set<String> fieldNames(JsonNode node) {
    return Set.copyOf(node.propertyNames());
  }
}
