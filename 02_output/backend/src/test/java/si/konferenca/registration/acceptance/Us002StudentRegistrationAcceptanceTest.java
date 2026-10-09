package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Api.array;
import static si.konferenca.registration.acceptance.support.Api.assertFieldError;
import static si.konferenca.registration.acceptance.support.Api.assertNothingStoredFor;
import static si.konferenca.registration.acceptance.support.Api.cell;
import static si.konferenca.registration.acceptance.support.Api.exportRowsFor;
import static si.konferenca.registration.acceptance.support.Api.external;
import static si.konferenca.registration.acceptance.support.Api.jsonCopies;
import static si.konferenca.registration.acceptance.support.Api.register;
import static si.konferenca.registration.acceptance.support.Api.student;
import static si.konferenca.registration.acceptance.support.Api.uniqueEmail;
import static si.konferenca.registration.acceptance.support.Api.workbookRows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Api.Response;
import si.konferenca.registration.acceptance.support.Backend;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** US-002 Student registration, through the REST API. */
class Us002StudentRegistrationAcceptanceTest {

  private static Backend backend;

  @BeforeAll
  static void start() {
    backend = Backend.startDefault();
  }

  @AfterAll
  static void stop() {
    backend.close();
  }

  private static List<String> ids(JsonNode array, String property) {
    List<String> values = new ArrayList<>();
    array.forEach(n -> values.add(n.get(property).asString()));
    return values;
  }

  @Test
  void AC_002_01_studentFormAsksForExactlyTheStudentFields() {
    Response response = Api.get(backend.url("/api/registration-form/student"));

    assertThat(response.status()).isEqualTo(200);
    JsonNode form = response.json();
    assertThat(form.get("type").asString()).isEqualTo("STUDENT");
    assertThat(ids(form.get("fields"), "name"))
        .containsExactly(
            "firstName", "lastName", "email", "studyInstitution", "studyProgramme", "studentId");
    assertThat(ids(form.get("consents"), "id")).contains("data-processing");
  }

  @Test
  void AC_002_02_validStudentRegistrationIsAccepted() {
    String email = uniqueEmail();

    Response response = register(backend, student(email));

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    assertThat(response.json().get("type").asString()).isEqualTo("STUDENT");
    List<List<String>> rows = workbookRows(Api.export(backend));
    List<String> row = exportRowsFor(backend, email).getFirst();
    assertThat(cell(rows, row, "Study institution")).isEqualTo("Univerza v Mariboru");
    assertThat(cell(rows, row, "Study programme")).isEqualTo("Informatika");
    assertThat(cell(rows, row, "Student ID")).isEqualTo("E1234567");
  }

  @ParameterizedTest
  @CsvSource({
    "studyInstitution, ''",
    "studyProgramme, ''",
    "studentId, ''",
    "studyInstitution, '   '",
    "studyProgramme, ' \t '",
    "studentId, ' '"
  })
  void AC_002_03_emptyOrWhitespaceStudentFieldIsRejected(String field, String value) {
    String email = uniqueEmail();
    ObjectNode body = student(email);
    body.put(field, value);
    int copies = jsonCopies(backend).size();

    Response response = register(backend, body);

    assertFieldError(response, 400, field, "REQUIRED");
    assertNothingStoredFor(backend, email, copies);
  }

  @ParameterizedTest
  @ValueSource(strings = {"luka.kranjc", "luka@", "luka kranjc@um.si"})
  void AC_002_04_invalidEmailFormatIsRejected(String invalid) {
    int copies = jsonCopies(backend).size();

    Response response = register(backend, student(invalid));

    assertFieldError(response, 400, "email", "INVALID_EMAIL");
    assertThat(jsonCopies(backend)).hasSize(copies);
  }

  @Test
  void AC_002_05_onlyActiveOptionsAvailableToStudentsAreOffered() {
    JsonNode categories = Api.form(backend, "student").get("categories");

    assertThat(ids(categories, "category")).containsExactly("WORKSHOP", "EVENT", "MEAL", "OTHER");
    assertThat(ids(categories.get(0).get("options"), "id")).containsExactly("ws-testing-ai");
    assertThat(ids(categories.get(1).get("options"), "id"))
        .containsExactlyInAnyOrder("ev-gala-dinner", "ev-reception");
  }

  @Test
  void AC_002_06_optionNotAvailableToStudentsIsRejected() {
    String email = uniqueEmail();
    ObjectNode body = student(email);
    body.set("optionIds", array("ws-industry-masterclass"));
    int copies = jsonCopies(backend).size();

    Response response = register(backend, body);

    assertFieldError(response, 400, "optionIds", "OPTION_NOT_AVAILABLE");
    assertNothingStoredFor(backend, email, copies);
  }

  @Test
  void AC_002_07_missingMandatoryConsentIsRejected() {
    String email = uniqueEmail();
    ObjectNode body = student(email);
    body.set("consentIds", array());
    int copies = jsonCopies(backend).size();

    Response response = register(backend, body);

    assertFieldError(response, 400, "consentIds", "CONSENT_REQUIRED");
    assertNothingStoredFor(backend, email, copies);
  }

  @Test
  void AC_002_08_emailAlreadyRegisteredAsExternalIsRejectedForAStudent() {
    String email = uniqueEmail();
    assertThat(register(backend, external(email)).status()).isEqualTo(201);
    int copies = jsonCopies(backend).size();

    Response response = register(backend, student(email.toUpperCase()));

    assertFieldError(response, 409, "email", "ALREADY_REGISTERED");
    assertThat(exportRowsFor(backend, email)).hasSize(1);
    assertThat(jsonCopies(backend)).hasSize(copies);
  }

  @Test
  void AC_002_09_failedAntiAutomationCheckIsRejected() {
    String email = uniqueEmail();
    ObjectNode body = student(email);
    body.put("recaptchaToken", "wrong");
    int copies = jsonCopies(backend).size();

    Response response = register(backend, body);

    assertFieldError(response, 400, "recaptchaToken", "CAPTCHA_FAILED");
    assertNothingStoredFor(backend, email, copies);
  }
}
