package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Payloads.external;
import static si.konferenca.registration.acceptance.support.Payloads.student;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.CopySchema;
import si.konferenca.registration.acceptance.support.Database;
import si.konferenca.registration.acceptance.support.JsonCopies;
import tools.jackson.databind.JsonNode;

/** US-005 every accepted registration is stored in the database and as a JSON copy. */
class StorageAcceptanceTest extends AcceptanceTest {

  /** D-12: the JDBC driver returns TIMESTAMPTZ values as java.sql.Timestamp. */
  private static OffsetDateTime timestamp(Object value) {
    return ((java.sql.Timestamp) value).toInstant().atOffset(ZoneOffset.UTC);
  }

  @Test
  @DisplayName("AC-005-01 the database holds every field, the options and the timed consents")
  void ac005_01_storesRegistrationRow() {
    Map<String, Object> payload = external();
    payload.put("consents", List.of("privacy", "newsletter"));
    String email = (String) payload.get("email");
    OffsetDateTime before = OffsetDateTime.now().minusSeconds(5);

    Api.Response response = api.register(payload);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    String id = response.json().get("id").asString();
    Map<String, Object> row = Database.registrationByEmail(email);
    assertThat(row).isNotNull();
    assertThat(row.get("id").toString()).isEqualTo(id);
    assertThat(row.get("type")).isEqualTo("EXTERNAL");
    assertThat(row.get("first_name")).isEqualTo("Ana");
    assertThat(row.get("last_name")).isEqualTo("Novak");
    assertThat(row.get("email")).isEqualTo(email);
    assertThat(row.get("organization")).isEqualTo("Institut Jožef Stefan");
    assertThat(timestamp(row.get("submitted_at"))).isAfter(before);

    List<String> options = new ArrayList<>();
    Database.options(row.get("id"))
        .forEach(
            o ->
                options.add(
                    o.get("option_id") + "|" + o.get("option_name") + "|" + o.get("category")));
    assertThat(options)
        .containsExactly(
            "ws-ai|Delavnica umetne inteligence|workshop", "meal-lunch|Kosilo, dan 1|meal");

    List<Map<String, Object>> consents = Database.consents(row.get("id"));
    assertThat(consents)
        .extracting(c -> c.get("consent_id"))
        .containsExactly("newsletter", "privacy");
    for (Map<String, Object> c : consents) {
      assertThat(timestamp(c.get("given_at"))).isAfter(before);
    }
  }

  @Test
  @DisplayName("AC-005-02 an external registration has a JSON copy valid against the schema")
  void ac005_02_writesExternalJsonCopy() {
    Map<String, Object> payload = external();

    Api.Response response = api.register(payload);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    String id = response.json().get("id").asString();
    Path copy = JsonCopies.copyOf(jsonDir(), id);
    assertThat(copy).exists();
    JsonNode doc = Api.JSON.readTree(JsonCopies.read(copy));
    assertThat(CopySchema.violations(doc)).isEmpty();
    assertThat(doc.get("id").asString()).isEqualTo(id);
    assertThat(doc.get("type").asString()).isEqualTo("EXTERNAL");
    assertThat(doc.get("firstName").asString()).isEqualTo("Ana");
    assertThat(doc.get("lastName").asString()).isEqualTo("Novak");
    assertThat(doc.get("email").asString()).isEqualTo(payload.get("email"));
    assertThat(doc.get("organization").asString()).isEqualTo("Institut Jožef Stefan");
    assertThat(doc.get("options")).hasSize(2);
    assertThat(doc.get("options").get(0).get("id").asString()).isEqualTo("ws-ai");
    assertThat(doc.get("consents").get(0).get("id").asString()).isEqualTo("privacy");
    assertThat(JsonCopies.read(copy)).doesNotContain("test-mode-pass");
  }

  @Test
  @DisplayName("AC-005-02 a student registration has a JSON copy valid against the schema")
  void ac005_02_writesStudentJsonCopy() throws Exception {
    Map<String, Object> payload = student();

    Api.Response response = api.register(payload);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    String id = response.json().get("id").asString();
    Path copy = JsonCopies.copyOf(jsonDir(), id);
    assertThat(copy).exists();
    byte[] bytes = Files.readAllBytes(copy);
    assertThat(bytes[0]).as("no byte order mark").isEqualTo((byte) '{');
    JsonNode doc = Api.JSON.readTree(bytes);
    assertThat(CopySchema.violations(doc)).isEmpty();
    assertThat(doc.get("studyProgramme").asString()).isEqualTo("Računalništvo in informatika");
    assertThat(doc.has("organization")).isFalse();
    assertThat(
            Duration.between(
                    OffsetDateTime.parse(doc.get("submittedAt").asString()), OffsetDateTime.now())
                .abs())
        .isLessThan(Duration.ofMinutes(5));
  }
}
