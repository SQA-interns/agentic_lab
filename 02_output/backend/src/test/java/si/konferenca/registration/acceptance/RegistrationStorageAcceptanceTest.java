package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Fixtures;
import tools.jackson.databind.JsonNode;

/** US-005 reliable storage: database row and raw JSON copy (BR-07). */
class RegistrationStorageAcceptanceTest extends AcceptanceTestBase {

  @Test
  @DisplayName("AC-005-01 the database holds type, fields, options and timestamped consents")
  void ac00501RegistrationIsStoredInDatabase() {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.external(email);
    request.put("consentIds", List.of("data-processing", "photos"));

    Api.Response r = api.register(request);

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    String reference = r.json().path("reference").asString();
    Map<String, Object> row = db.registrationByReference(reference);
    assertThat(row).isNotNull();
    assertThat(row.get("type")).isEqualTo("EXTERNAL");
    assertThat(row.get("first_name")).isEqualTo("Ana");
    assertThat(row.get("last_name")).isEqualTo("Novak");
    assertThat(row.get("email")).isEqualTo(email);
    assertThat(row.get("organization")).isEqualTo("Institut Jožef Stefan");
    assertThat(row.get("submitted_at")).isNotNull();
    assertThat(db.optionIds(reference)).containsExactly("meal-lunch-day1", "ws-secure-web");
    List<Map<String, Object>> consents = db.consents(reference);
    assertThat(consents)
        .extracting(c -> c.get("consent_id"))
        .containsExactlyInAnyOrder("data-processing", "photos");
    assertThat(consents).allSatisfy(c -> assertThat(c.get("given_at")).isNotNull());
    assertThat(consents)
        .anySatisfy(
            c ->
                assertThat((String) c.get("consent_text"))
                    .startsWith("I agree that the organizers process"));
  }

  @Test
  @DisplayName("AC-005-02 a JSON copy with the accepted registration is written")
  void ac00502JsonCopyIsWritten() {
    String email = Fixtures.uniqueEmail();

    Api.Response r = api.register(Fixtures.student(email));

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    String reference = r.json().path("reference").asString();
    Path copy = jsonCopy(jsonCopyDir(), reference);
    assertThat(copy).isRegularFile();
    JsonNode json = Api.JSON.readTree(read(copy));
    assertThat(json.path("schemaVersion").asInt()).isEqualTo(1);
    assertThat(json.path("reference").asString()).isEqualTo(reference);
    assertThat(json.path("type").asString()).isEqualTo("STUDENT");
    assertThat(OffsetDateTime.parse(json.path("submittedAt").asString())).isNotNull();
    JsonNode p = json.path("participant");
    assertThat(p.path("firstName").asString()).isEqualTo("Luka");
    assertThat(p.path("lastName").asString()).isEqualTo("Kovač");
    assertThat(p.path("email").asString()).isEqualTo(email);
    assertThat(p.path("studyInstitution").asString()).isEqualTo("Univerza v Ljubljani");
    assertThat(p.path("studyProgramme").asString()).isEqualTo("Računalništvo in informatika");
    assertThat(p.path("studentId").asString()).isEqualTo("63200001");
    assertThat(p.has("organization")).isFalse();
    assertThat(ConferenceOptionsAcceptanceTest.ids(json.path("options")))
        .containsExactlyInAnyOrder("ev-career-fair", "meal-lunch-day2");
    assertThat(json.path("options").get(0).path("name").asString()).isNotBlank();
    assertThat(ConferenceOptionsAcceptanceTest.ids(json.path("consents")))
        .containsExactly("data-processing");
    assertThat(json.path("consents").get(0).path("givenAt").asString()).isNotBlank();
    assertThat(Files.exists(copy)).isTrue();
  }
}
