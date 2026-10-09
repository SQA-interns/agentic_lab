package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Api.assertNothingStoredFor;
import static si.konferenca.registration.acceptance.support.Api.cell;
import static si.konferenca.registration.acceptance.support.Api.exportRowsFor;
import static si.konferenca.registration.acceptance.support.Api.external;
import static si.konferenca.registration.acceptance.support.Api.jsonCopies;
import static si.konferenca.registration.acceptance.support.Api.readJson;
import static si.konferenca.registration.acceptance.support.Api.register;
import static si.konferenca.registration.acceptance.support.Api.student;
import static si.konferenca.registration.acceptance.support.Api.uniqueEmail;
import static si.konferenca.registration.acceptance.support.Api.workbookRows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Api.Response;
import si.konferenca.registration.acceptance.support.Backend;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** US-005 Reliable registration storage: database (seen through the export) and JSON copy. */
class Us005StorageAcceptanceTest {

  private static Backend backend;

  @BeforeAll
  static void start() {
    backend = Backend.startDefault();
  }

  @AfterAll
  static void stop() {
    backend.close();
  }

  @Test
  void AC_005_01_acceptedRegistrationIsInTheExportWithEveryValue() {
    String email = uniqueEmail();
    Response response = register(backend, student(email));
    assertThat(response.status()).as(response.text()).isEqualTo(201);

    List<List<String>> rows = workbookRows(Api.export(backend));
    List<List<String>> matching = exportRowsFor(backend, email);
    assertThat(matching).hasSize(1);
    List<String> row = matching.getFirst();
    assertThat(cell(rows, row, "Registration ID")).isEqualTo(response.json().get("id").asString());
    assertThat(cell(rows, row, "First name")).isEqualTo("Luka");
    assertThat(cell(rows, row, "Last name")).isEqualTo("Kranjc");
    assertThat(cell(rows, row, "Study institution")).isEqualTo("Univerza v Mariboru");
    assertThat(cell(rows, row, "Study programme")).isEqualTo("Informatika");
    assertThat(cell(rows, row, "Student ID")).isEqualTo("E1234567");
    assertThat(cell(rows, row, "Workshops")).isEqualTo("Delavnica: testiranje sistemov UI");
    assertThat(cell(rows, row, "Consents")).isEqualTo("data-processing");
  }

  @Test
  void AC_005_02_acceptedRegistrationHasARawJsonCopyOfExactlyTheAcceptedData() {
    String email = uniqueEmail();
    ObjectNode body = external(email);
    body.put("firstName", " Ana ");

    Response response = register(backend, body);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    String id = response.json().get("id").asString();
    Path copy = backend.jsonCopyDir().resolve(id + ".json");
    assertThat(copy).exists();
    JsonNode json = readJson(copy);
    assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
    assertThat(json.get("id").asString()).isEqualTo(id);
    assertThat(json.get("type").asString()).isEqualTo("EXTERNAL");
    JsonNode participant = json.get("participant");
    assertThat(participant.get("firstName").asString()).isEqualTo("Ana");
    assertThat(participant.get("lastName").asString()).isEqualTo("Novak");
    assertThat(participant.get("email").asString()).isEqualTo(email);
    assertThat(participant.get("organization").asString()).isEqualTo("Institut Jožef Stefan");
    List<String> options = new ArrayList<>();
    json.get("selectedOptions").forEach(o -> options.add(o.get("id").asString()));
    assertThat(options).containsExactlyInAnyOrder("ws-testing-ai", "meal-lunch-day1");
    assertThat(json.get("consents").get(0).get("id").asString()).isEqualTo("data-processing");
    assertThat(json.get("consents").get(0).has("givenAt")).isTrue();
    assertThat(json.toString()).doesNotContain("recaptcha").doesNotContain(Api.TEST_CAPTCHA_TOKEN);
  }

  @Test
  void AC_005_03_registrationIsNotAcceptedWhenTheJsonCopyCannotBeWritten() throws IOException {
    String email = uniqueEmail();
    int copies = jsonCopies(backend).size();
    var original = Files.getPosixFilePermissions(backend.jsonCopyDir());
    Files.setPosixFilePermissions(
        backend.jsonCopyDir(), PosixFilePermissions.fromString("r-x------"));
    Response response;
    try {
      response = register(backend, external(email));
    } finally {
      Files.setPosixFilePermissions(backend.jsonCopyDir(), original);
    }

    assertThat(response.status()).isNotEqualTo(201);
    assertThat(response.status()).isEqualTo(503);
    assertNothingStoredFor(backend, email, copies);
  }

  @Test
  void AC_005_04_registrationsSurviveARestart() {
    Backend own = Backend.startDefault();
    try {
      String email = uniqueEmail();
      Response response = register(own, external(email));
      assertThat(response.status()).as(response.text()).isEqualTo(201);
      String id = response.json().get("id").asString();

      own = own.restart();

      assertThat(exportRowsFor(own, email)).hasSize(1);
      assertThat(own.jsonCopyDir().resolve(id + ".json")).exists();
    } finally {
      own.close();
    }
  }

  @Test
  void AC_005_05_rejectedRegistrationLeavesNoRowAndNoJsonCopy() {
    String email = uniqueEmail();
    ObjectNode body = external(email);
    body.put("organization", " ");
    int copies = jsonCopies(backend).size();

    Response response = register(backend, body);

    assertThat(response.status()).isEqualTo(400);
    assertNothingStoredFor(backend, email, copies);
  }
}
