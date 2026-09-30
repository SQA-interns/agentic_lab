package lab.conference.acceptance;

import static lab.conference.acceptance.support.Checks.assertNothingStored;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lab.conference.acceptance.support.Api;
import lab.conference.acceptance.support.AppInstance;
import lab.conference.acceptance.support.Payloads;
import lab.conference.acceptance.support.Store;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** US-002: student registration through the REST API. */
class StudentRegistrationAcceptanceTest {

  private static AppInstance app;
  private static Api api;

  @BeforeAll
  static void start() {
    app = AppInstance.builder().build().start();
    api = app.api();
  }

  @AfterAll
  static void stop() {
    app.stop();
  }

  @Test
  void ac_002_01_validStudentSubmissionIsAcceptedWithStudyDetails() throws Exception {
    Map<String, Object> body = Payloads.student();
    body.put("studentId", "ab 12/Č-9");

    Api.Response r = api.postStudent(body);

    assertThat(r.status()).as(r.toString()).isEqualTo(201);
    JsonNode accepted = r.json();
    String id = accepted.path("registrationId").asText();
    assertThat(id).matches("[0-9a-f-]{36}").isNotEqualTo(body.get("clientRequestId"));
    assertThat(accepted.path("formType").asText()).isEqualTo("student");
    Map<String, Object> row = app.store().registrationRow(id);
    assertThat(row)
        .containsEntry("study_institution", "Synthetic University")
        .containsEntry("study_programme", "Computer Science")
        .containsEntry("student_id", "ab 12/Č-9");
    JsonNode participant =
        new ObjectMapper()
            .readTree(Files.readAllBytes(app.store().jsonFile(id)))
            .path("participant");
    assertThat(participant.path("studentId").asText()).isEqualTo("ab 12/Č-9");
    assertThat(participant.has("organization")).isFalse();
  }

  static Stream<Arguments> requiredFieldViolations() {
    Stream.Builder<Arguments> b = Stream.builder();
    for (String field :
        List.of(
            "firstName", "lastName", "email", "studyInstitution", "studyProgramme", "studentId")) {
      b.add(Arguments.of(field, null));
      b.add(Arguments.of(field, ""));
      b.add(Arguments.of(field, "   "));
    }
    return b.build();
  }

  @ParameterizedTest(name = "AC-002-02 {0}=[{1}]")
  @MethodSource("requiredFieldViolations")
  void ac_002_02_missingOrBlankRequiredFieldIsRejected(String field, String value) {
    Map<String, Object> body = Payloads.student();
    if (value == null) {
      body.remove(field);
    } else {
      body.put(field, value);
    }
    Store.Snapshot before = app.store().snapshot();

    Api.Response r = api.postStudent(body);

    assertThat(r.status()).as(r.toString()).isEqualTo(400);
    assertThat(r.hasFieldError(field, "REQUIRED")).as(r.toString()).isTrue();
    assertNothingStored(app, before, "email".equals(field) ? null : (String) body.get("email"));
  }

  @ParameterizedTest(name = "AC-002-03 email=[{0}]")
  @ValueSource(strings = {"student.example.test", "student@", "a b@example.test", "x@y"})
  void ac_002_03_invalidEmailIsRejected(String email) {
    Map<String, Object> body = Payloads.student();
    body.put("email", email);
    Store.Snapshot before = app.store().snapshot();

    Api.Response r = api.postStudent(body);

    assertThat(r.status()).as(r.toString()).isEqualTo(400);
    assertThat(r.hasFieldError("email", "INVALID_EMAIL")).as(r.toString()).isTrue();
    assertNothingStored(app, before, null);
  }

  @Test
  void ac_002_04_unicodeValuesAreTrimmedAndPreserved() throws Exception {
    Map<String, Object> body = Payloads.student();
    body.put("firstName", " Nuša ");
    body.put("studyInstitution", " Fakulteta za računalništvo ");
    body.put("studyProgramme", "Računalništvo – UNI ");
    body.put("studentId", " 6320ŽČ ");

    Api.Response r = api.postStudent(body);

    assertThat(r.status()).as(r.toString()).isEqualTo(201);
    JsonNode participant =
        new ObjectMapper()
            .readTree(
                Files.readAllBytes(app.store().jsonFile(r.json().path("registrationId").asText())))
            .path("participant");
    assertThat(participant.path("firstName").asText()).isEqualTo("Nuša");
    assertThat(participant.path("studyInstitution").asText())
        .isEqualTo("Fakulteta za računalništvo");
    assertThat(participant.path("studyProgramme").asText()).isEqualTo("Računalništvo – UNI");
    assertThat(participant.path("studentId").asText()).isEqualTo("6320ŽČ");
  }

  @Test
  void ac_002_05_invalidOptionsAreRejected() {
    for (Object selections :
        List.of(
            Payloads.selections(List.of(), List.of(), List.of("meal-off"), List.of()),
            Payloads.selections(List.of(), List.of(), List.of(), List.of("no-such-option")),
            Payloads.selections(List.of(), List.of(), List.of("oth-poster"), List.of()),
            Payloads.selections(List.of(), List.of("ev-gala", "ev-gala"), List.of(), List.of()))) {
      Map<String, Object> body = Payloads.student();
      body.put("selections", selections);
      Store.Snapshot before = app.store().snapshot();

      Api.Response r = api.postStudent(body);

      assertThat(r.status()).as(selections + " " + r).isEqualTo(400);
      assertNothingStored(app, before, (String) body.get("email"));
    }
  }

  @Test
  void ac_002_05_anyActiveOptionFromEveryGroupIsAcceptedForStudents() {
    Map<String, Object> body = Payloads.student();
    body.put(
        "selections",
        Payloads.selections(
            List.of("ws-alpha", "ws-beta"),
            List.of("ev-gala"),
            List.of("meal-veg"),
            List.of("oth-poster")));
    assertThat(api.postStudent(body).status()).isEqualTo(201);
  }

  @ParameterizedTest(name = "AC-002-06 captcha=[{0}]")
  @ValueSource(strings = {"<missing>", "invalid"})
  void ac_002_06_missingOrInvalidCaptchaIsRejected(String token) {
    Map<String, Object> body = Payloads.student();
    if ("<missing>".equals(token)) {
      body.remove("captchaToken");
    } else {
      body.put("captchaToken", token);
    }
    Store.Snapshot before = app.store().snapshot();

    Api.Response r = api.postStudent(body);

    assertThat(r.status()).as(r.toString()).isEqualTo(400);
    assertThat(r.hasFieldError("captchaToken", null)).as(r.toString()).isTrue();
    assertNothingStored(app, before, (String) body.get("email"));
  }

  @Test
  void ac_002_07_consentIsEnforcedAndStored() throws Exception {
    Map<String, Object> rejected = Payloads.student();
    rejected.put("consentGiven", false);
    Store.Snapshot before = app.store().snapshot();
    Api.Response no = api.postStudent(rejected);
    assertThat(no.status()).as(no.toString()).isEqualTo(400);
    assertThat(no.hasFieldError("consentGiven", "CONSENT_REQUIRED")).as(no.toString()).isTrue();
    assertNothingStored(app, before, (String) rejected.get("email"));

    Api.Response yes = api.postStudent(Payloads.student());
    assertThat(yes.status()).isEqualTo(201);
    String id = yes.json().path("registrationId").asText();
    assertThat(app.store().registrationRow(id)).containsEntry("consent_given", true);
  }
}
