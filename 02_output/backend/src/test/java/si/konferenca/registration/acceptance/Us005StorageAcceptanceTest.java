package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.CONSENT_DATA;
import static si.konferenca.registration.acceptance.support.Registrations.CONSENT_PHOTO;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_MEAL;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_WORKSHOP;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.student;
import static si.konferenca.registration.acceptance.support.Registrations.withConsents;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Database;
import si.konferenca.registration.acceptance.support.JsonCopies;
import si.konferenca.registration.acceptance.support.Mailpit;
import tools.jackson.databind.JsonNode;

/** US-005 Reliable registration storage: database row and raw JSON copy, both or neither. */
class Us005StorageAcceptanceTest extends AcceptanceTestBase {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    configure(registry, Map.of());
  }

  @Test
  void ac005_01_acceptedRegistrationIsStoredWithFieldsOptionsAndConsents() {
    Instant before = Instant.now().minusSeconds(1);
    Api.Response response = api.register(withConsents(external(), CONSENT_DATA, CONSENT_PHOTO));

    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(201);
    String id = response.json().path("registrationId").asString();
    Map<String, Object> row =
        Database.rows(
                "SELECT type, first_name, last_name, email, organization, received_at"
                    + " FROM registration WHERE id = ?::uuid",
                id)
            .get(0);
    assertThat(row)
        .containsEntry("type", "external")
        .containsEntry("first_name", "Ana")
        .containsEntry("last_name", "Novak")
        .containsEntry("email", "ana.novak@example.com")
        .containsEntry("organization", "Institut Jozef Stefan");
    assertThat(
            Database.rows(
                "SELECT option_id FROM registration_option WHERE registration_id = ?::uuid", id))
        .extracting(r -> r.get("option_id"))
        .containsExactlyInAnyOrder(OPTION_WORKSHOP, OPTION_MEAL);
    List<Map<String, Object>> consents =
        Database.rows(
            "SELECT consent_id, consent_text, given_at FROM registration_consent"
                + " WHERE registration_id = ?::uuid",
            id);
    assertThat(consents)
        .extracting(r -> r.get("consent_id"))
        .containsExactlyInAnyOrder(CONSENT_DATA, CONSENT_PHOTO);
    for (Map<String, Object> consent : consents) {
      assertThat((String) consent.get("consent_text")).isNotBlank();
      Instant givenAt = ((java.sql.Timestamp) consent.get("given_at")).toInstant();
      assertThat(givenAt).isAfter(before).isBefore(Instant.now().plusSeconds(1));
    }
  }

  @Test
  void ac005_02_acceptedRegistrationHasARawJsonCopyExactlyAsAccepted() {
    Api.Response response = api.register(student());

    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(201);
    String id = response.json().path("registrationId").asString();
    assertThat(JsonCopies.files()).containsExactly(JsonCopies.fileFor(id));
    JsonNode copy = JsonCopies.read(id);
    assertThat(copy.path("schemaVersion").asInt()).isEqualTo(1);
    assertThat(copy.path("registrationId").asString()).isEqualTo(id);
    assertThat(copy.path("type").asString()).isEqualTo("student");
    assertThat(OffsetDateTime.parse(copy.path("receivedAt").asString())).isNotNull();
    JsonNode participant = copy.path("participant");
    assertThat(participant.path("firstName").asString()).isEqualTo("Luka");
    assertThat(participant.path("lastName").asString()).isEqualTo("Kranjc");
    assertThat(participant.path("email").asString()).isEqualTo("luka.kranjc@student.example.com");
    assertThat(participant.path("studyInstitution").asString()).isEqualTo("Univerza v Mariboru");
    assertThat(participant.path("studyProgramme").asString()).isEqualTo("Informatika");
    assertThat(participant.path("studentId").asString()).isEqualTo("93120045");
    assertThat(participant.has("organization")).isFalse();
    List<String> options = new ArrayList<>();
    copy.path("options").forEach(o -> options.add(o.path("id").asString()));
    assertThat(options).containsExactlyInAnyOrder("ws-testing", "other-tour");
    assertThat(copy.path("consents").get(0).path("id").asString()).isEqualTo(CONSENT_DATA);
    assertThat(copy.has("captchaToken")).isFalse();
  }

  @Test
  void ac005_03_registrationIsNotAcceptedWhenTheJsonCopyCannotBeWritten() {
    JsonCopies.makeUnwritable();

    Api.Response response = api.register(external());

    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(500);
    assertThat(Database.registrationCount()).as("database records").isZero();
  }

  @Test
  void ac005_04_registrationIsNotAcceptedWhenTheDatabaseRecordCannotBeWritten() {
    Database.failOnCommit();

    Api.Response response = api.register(external());

    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(500);
    Database.removeFaults();
    assertThat(JsonCopies.files()).as("JSON copies").isEmpty();
    assertThat(Database.registrationCount()).isZero();
  }

  @Test
  void ac005_05_noEmailIsSentWhenStorageFails() {
    Database.failOnCommit();

    Api.Response response = api.register(external());

    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(500);
    assertThat(Mailpit.messagesAfterQuietPeriod()).isEmpty();
  }
}
