package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * US-005 Reliable registration storage and the storage part of US-004, through the SQL and JSON
 * copy contracts.
 */
class Us005ReliableStorageAcceptanceTest extends AcceptanceTestBase {

  private static Instant instant(Object databaseValue) {
    if (databaseValue instanceof java.sql.Timestamp timestamp) {
      return timestamp.toInstant();
    }
    return ((OffsetDateTime) databaseValue).toInstant();
  }

  private static JsonNode jsonCopy(Stack.App app, UUID id) throws IOException {
    Path file = app.jsonCopyDir().resolve(id + ".json");
    assertThat(file).isRegularFile();
    return Api.JSON.readTree(Files.readAllBytes(file));
  }

  private static List<String> fieldNames(JsonNode node) {
    List<String> names = new ArrayList<>();
    Iterator<String> iterator = node.propertyNames().iterator();
    iterator.forEachRemaining(names::add);
    return names;
  }

  @Test
  void ac_005_01_externalRegistrationIsStoredInTheDatabase() {
    Instant before = Instant.now().minusSeconds(5);
    Api.Reply reply = Api.register(Stack.app(), external());
    UUID id = assertAccepted(reply);
    Instant acceptedAt =
        OffsetDateTime.parse(reply.json().path("acceptedAt").asString()).toInstant();

    Map<String, Object> row = registrationRow(id);
    assertThat(row.get("type")).isEqualTo("EXTERNAL");
    assertThat(row.get("first_name")).isEqualTo("Ana");
    assertThat(row.get("last_name")).isEqualTo("Novak");
    assertThat(row.get("email")).isEqualTo("ana.novak@example.org");
    assertThat(row.get("organization")).isEqualTo("Podjetje Primer");
    assertThat(row.get("study_institution")).isNull();
    assertThat(row.get("study_programme")).isNull();
    assertThat(row.get("student_id")).isNull();
    assertThat(row.get("consent_id")).isEqualTo("personal-data");
    assertThat(row.get("consent_text")).isEqualTo(Stack.CONSENT_TEXT);
    assertThat(instant(row.get("consent_given_at")))
        .isBetween(before, Instant.now().plusSeconds(5));
    assertThat(instant(row.get("accepted_at"))).isCloseTo(acceptedAt, within(1, ChronoUnit.MILLIS));
    assertThat(acceptedAt).isBetween(before, Instant.now().plusSeconds(5));

    List<Map<String, Object>> options = optionRows(id);
    assertThat(options)
        .extracting(option -> option.get("option_id"))
        .containsExactly("ws-testing", "meal-lunch-day1");
    assertThat(options)
        .extracting(option -> option.get("option_name"))
        .containsExactly("Delavnica: testiranje programske opreme", "Kosilo, prvi dan");
    assertThat(options)
        .extracting(option -> option.get("option_category"))
        .containsExactly("workshop", "meal");
  }

  @Test
  void ac_005_01_studentRegistrationIsStoredInTheDatabase() {
    UUID id = assertAccepted(Api.register(Stack.app(), student()));

    Map<String, Object> row = registrationRow(id);
    assertThat(row.get("type")).isEqualTo("STUDENT");
    assertThat(row.get("first_name")).isEqualTo("Luka");
    assertThat(row.get("last_name")).isEqualTo("Kovač");
    assertThat(row.get("email")).isEqualTo("luka.kovac@example.org");
    assertThat(row.get("organization")).isNull();
    assertThat(row.get("study_institution")).isEqualTo("Univerza v Ljubljani");
    assertThat(row.get("study_programme")).isEqualTo("Računalništvo in informatika");
    assertThat(row.get("student_id")).isEqualTo("63210001");
    assertThat(row.get("consent_id")).isEqualTo("personal-data");
    assertThat(optionRows(id))
        .extracting(option -> option.get("option_id"))
        .containsExactly("ev-opening", "other-city-tour");
  }

  @Test
  void ac_005_02_externalRegistrationHasARawJsonCopy() throws IOException {
    Api.Reply reply = Api.register(Stack.app(), external());
    UUID id = assertAccepted(reply);

    assertThat(Stack.jsonCopies(Stack.app())).hasSize(1);
    JsonNode copy = jsonCopy(Stack.app(), id);
    assertThat(fieldNames(copy))
        .containsExactlyInAnyOrder(
            "schemaVersion",
            "id",
            "type",
            "acceptedAt",
            "firstName",
            "lastName",
            "email",
            "organization",
            "options",
            "consent");
    assertThat(copy.path("schemaVersion").asInt()).isEqualTo(1);
    assertThat(copy.path("id").asString()).isEqualTo(id.toString());
    assertThat(copy.path("type").asString()).isEqualTo("EXTERNAL");
    assertThat(OffsetDateTime.parse(copy.path("acceptedAt").asString()).toInstant())
        .isEqualTo(OffsetDateTime.parse(reply.json().path("acceptedAt").asString()).toInstant());
    assertThat(copy.path("firstName").asString()).isEqualTo("Ana");
    assertThat(copy.path("lastName").asString()).isEqualTo("Novak");
    assertThat(copy.path("email").asString()).isEqualTo("ana.novak@example.org");
    assertThat(copy.path("organization").asString()).isEqualTo("Podjetje Primer");
    assertThat(copy.path("options")).hasSize(2);
    assertThat(fieldNames(copy.path("options").get(0)))
        .containsExactlyInAnyOrder("id", "name", "category");
    assertThat(copy.path("options").get(0).path("id").asString()).isEqualTo("ws-testing");
    assertThat(copy.path("options").get(0).path("name").asString())
        .isEqualTo("Delavnica: testiranje programske opreme");
    assertThat(copy.path("options").get(0).path("category").asString()).isEqualTo("workshop");
    assertThat(copy.path("options").get(1).path("id").asString()).isEqualTo("meal-lunch-day1");
    assertThat(fieldNames(copy.path("consent"))).containsExactlyInAnyOrder("id", "text", "givenAt");
    assertThat(copy.path("consent").path("id").asString()).isEqualTo("personal-data");
    assertThat(copy.path("consent").path("text").asString()).isEqualTo(Stack.CONSENT_TEXT);
    assertThat(OffsetDateTime.parse(copy.path("consent").path("givenAt").asString())).isNotNull();
  }

  @Test
  void ac_005_02_studentRegistrationHasARawJsonCopy() throws IOException {
    Map<String, Object> registration = student();
    registration.put("firstName", "Žan");
    registration.put("lastName", "Šuštar Čeh");
    UUID id = assertAccepted(Api.register(Stack.app(), registration));

    JsonNode copy = jsonCopy(Stack.app(), id);
    assertThat(fieldNames(copy))
        .containsExactlyInAnyOrder(
            "schemaVersion",
            "id",
            "type",
            "acceptedAt",
            "firstName",
            "lastName",
            "email",
            "studyInstitution",
            "studyProgramme",
            "studentId",
            "options",
            "consent");
    assertThat(copy.path("type").asString()).isEqualTo("STUDENT");
    assertThat(copy.path("firstName").asString()).isEqualTo("Žan");
    assertThat(copy.path("lastName").asString()).isEqualTo("Šuštar Čeh");
    assertThat(copy.path("studyInstitution").asString()).isEqualTo("Univerza v Ljubljani");
    assertThat(copy.path("studyProgramme").asString()).isEqualTo("Računalništvo in informatika");
    assertThat(copy.path("studentId").asString()).isEqualTo("63210001");
    assertThat(copy.path("options")).hasSize(2);
  }

  @Test
  void ac_005_03_ac_004_03_registrationIsNotAcceptedWhenTheJsonCopyCannotBeWritten()
      throws IOException {
    Path directory = Stack.newDirectory("json-copies-broken");
    try (Stack.App app = Stack.startApp(Map.of("JSON_COPY_DIR", directory.toString()))) {
      assertAccepted(Api.register(app, external()));
      Stack.reset();
      for (Path file : Stack.jsonCopies(app)) {
        Files.delete(file);
      }
      // The directory is replaced by a file, so no copy can be written into it.
      Files.delete(directory);
      Files.writeString(directory, "not a directory");

      Api.Reply reply = Api.register(app, external());

      assertThat(reply.status()).as("status of %s", reply.text()).isEqualTo(503);
      assertThat(reply.header("Content-Type")).startsWith("application/problem+json");
      assertThat(reply.text()).doesNotContain("Exception", "java.", "json-copies-broken");
      assertThat(Stack.registrationCount()).as("registrations in the database").isZero();
      assertThat(Mailbox.messagesAfterSettling()).as("emails").isEmpty();
    } finally {
      Files.deleteIfExists(directory);
    }
  }

  @Test
  void ac_005_03_ac_004_03_registrationIsNotAcceptedWhenTheDatabaseCannotBeWritten() {
    assertAccepted(Api.register(Stack.app(), external()));
    Stack.reset();
    Stack.execute("ALTER TABLE registration RENAME TO registration_unavailable");
    try {
      Api.Reply reply = Api.register(Stack.app(), external());

      assertThat(reply.status()).as("status of %s", reply.text()).isEqualTo(503);
      assertThat(reply.header("Content-Type")).startsWith("application/problem+json");
      assertThat(reply.text())
          .doesNotContain("Exception", "java.", "registration_unavailable", "SQL");
      assertThat(Stack.jsonCopies(Stack.app())).as("JSON copies").isEmpty();
      assertThat(Mailbox.messagesAfterSettling()).as("emails").isEmpty();
    } finally {
      Stack.execute("ALTER TABLE registration_unavailable RENAME TO registration");
    }
    assertThat(Stack.registrationCount()).as("registrations in the database").isZero();
  }

  @Test
  void ac_005_04_rejectedRegistrationIsNotStored() {
    Api.Reply reply = Api.register(Stack.app(), with(student(), "email", "not-an-email"));

    assertThat(reply.status()).isEqualTo(400);
    assertThat(Stack.registrationCount()).as("registrations in the database").isZero();
    assertThat(Stack.rows("SELECT option_id FROM registration_option")).isEmpty();
    assertThat(Stack.jsonCopies(Stack.app())).as("JSON copies").isEmpty();
  }

  @Test
  void ac_005_05_storedRegistrationsSurviveARestart() throws IOException {
    Path directory = Stack.newDirectory("json-copies-restart");
    Map<String, String> settings = Map.of("JSON_COPY_DIR", directory.toString());
    UUID id;
    byte[] copyBeforeRestart;
    try (Stack.App app = Stack.startApp(settings)) {
      id = assertAccepted(Api.register(app, external()));
      copyBeforeRestart = Files.readAllBytes(directory.resolve(id + ".json"));
    }

    try (Stack.App restarted = Stack.startApp(settings)) {
      assertThat(registrationRow(id).get("email")).isEqualTo("ana.novak@example.org");
      assertThat(optionRows(id)).hasSize(2);
      assertThat(Files.readAllBytes(directory.resolve(id + ".json"))).isEqualTo(copyBeforeRestart);
      Api.Reply export =
          Api.getAsOrganizer(
              restarted,
              "/api/registrations/export",
              Stack.ORGANIZER_USERNAME,
              Stack.ORGANIZER_PASSWORD);
      assertThat(export.status()).isEqualTo(200);
      assertThat(Workbooks.rowsOf(export.body())).anyMatch(row -> row.contains(id.toString()));
    }
  }
}
