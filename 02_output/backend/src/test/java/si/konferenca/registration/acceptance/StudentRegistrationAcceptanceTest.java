package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.student;
import static si.konferenca.registration.acceptance.support.Registrations.with;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.acceptance.support.RunningApp;
import si.konferenca.registration.acceptance.support.RunningApp.Response;
import tools.jackson.databind.JsonNode;

/** US-002 Student registration, through the REST API (openapi.yaml). */
class StudentRegistrationAcceptanceTest {

  private static RunningApp app;

  @BeforeAll
  static void start() {
    app = RunningApp.start();
  }

  @AfterAll
  static void stop() {
    app.close();
  }

  @Test
  void AC_002_01_validStudentRegistrationIsAcceptedAsStudent() {
    Map<String, Object> reg = student();

    Response r = app.register(reg);

    assertThat(r.status()).isEqualTo(201);
    List<Map<String, Object>> rows = app.registrationRows((String) reg.get("email"));
    assertThat(rows).hasSize(1);
    Map<String, Object> row = rows.get(0);
    assertThat(row.get("registration_type")).isEqualTo("STUDENT");
    assertThat(row.get("study_institution")).isEqualTo("Univerza v Mariboru");
    assertThat(row.get("study_programme")).isEqualTo("Informatika");
    assertThat(row.get("student_id")).isEqualTo("93120001");
    assertThat(row.get("organization")).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "firstName",
        "lastName",
        "email",
        "studyInstitution",
        "studyProgramme",
        "studentId"
      })
  void AC_002_02_emptyRequiredStudentFieldIsRejectedAndIdentified(String field) {
    Map<String, Object> reg = student();

    Response r = app.register(with(reg, field, ""));

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.fieldErrors()).containsEntry(field, "REQUIRED");
    assertThat(app.registrationRows((String) reg.get("email"))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"studentId", "studyProgramme"})
  void AC_002_03_whitespaceOnlyStudentFieldIsRejected(String field) {
    Map<String, Object> reg = student();

    Response r = app.register(with(reg, field, "  \t  "));

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.fieldErrors()).containsEntry(field, "REQUIRED");
    assertThat(app.registrationRows((String) reg.get("email"))).isEmpty();
  }

  @Test
  void AC_002_04_invalidStudentEmailIsRejected() {
    Map<String, Object> reg = with(student(), "email", "luka.kranjc@student");

    Response r = app.register(reg);

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.fieldErrors()).containsEntry("email", "INVALID_EMAIL");
    assertThat(app.registrationRows("luka.kranjc@student")).isEmpty();
  }

  @Test
  void AC_002_05_optionNotOfferedToStudentsIsRejectedAndNothingStored() {
    Map<String, Object> reg = with(student(), "optionIds", List.of("ev-gala"));

    Response r = app.register(reg);

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.fieldErrors()).containsValue("OPTION_NOT_OFFERED");
    assertThat(app.registrationRows((String) reg.get("email"))).isEmpty();
  }

  @Test
  void AC_002_06_optionNotOfferedToStudentsIsMarkedSoForTheForm() {
    Response r = app.get("/api/options");

    assertThat(r.status()).isEqualTo(200);
    JsonNode gala =
        r.json()
            .path("options")
            .valueStream()
            .filter(o -> o.path("id").asString().equals("ev-gala"))
            .findFirst()
            .orElseThrow();
    List<String> offeredTo = gala.path("offeredTo").valueStream().map(JsonNode::asString).toList();
    assertThat(offeredTo).containsExactly("EXTERNAL");
    JsonNode tour =
        r.json()
            .path("options")
            .valueStream()
            .filter(o -> o.path("id").asString().equals("ev-tour"))
            .findFirst()
            .orElseThrow();
    assertThat(tour.path("offeredTo").valueStream().map(JsonNode::asString).toList())
        .containsExactlyInAnyOrder("EXTERNAL", "STUDENT");
  }

  @Test
  void AC_002_07_inactiveOptionIsRejectedForStudents() {
    Map<String, Object> reg = with(student(), "optionIds", List.of("meal-breakfast"));

    Response r = app.register(reg);

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.fieldErrors()).containsValue("INACTIVE_OPTION");
    assertThat(app.registrationRows((String) reg.get("email"))).isEmpty();
  }

  @Test
  void AC_002_08_studentRegistrationWithoutConsentIsRejected() {
    Map<String, Object> reg = with(student(), "consentGiven", false);

    Response r = app.register(reg);

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.fieldErrors()).containsEntry("consentGiven", "CONSENT_REQUIRED");
    assertThat(app.registrationRows((String) reg.get("email"))).isEmpty();
  }

  @Test
  void AC_002_09_externalFieldIsNotAllowedForStudents() {
    Map<String, Object> reg = with(student(), "organization", "Podjetje d.o.o.");

    Response r = app.register(reg);

    assertThat(r.status()).isEqualTo(400);
    assertThat(r.fieldErrors()).containsEntry("organization", "NOT_ALLOWED_FOR_TYPE");
    assertThat(app.registrationRows((String) reg.get("email"))).isEmpty();
  }
}
