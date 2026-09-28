package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** US-002 — Student registration. */
class StudentRegistrationAcceptanceTest extends AcceptanceTestBase {

  @Test
  void ac_002_01_validStudentRegistrationIsAccepted() {
    String email = uniqueEmail("ac00201");
    Resp r = register(validStudent(email));

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    JsonNode body = r.json();
    assertThat(body.path("type").asText()).isEqualTo("STUDENT");
    assertThat(body.path("studyInstitution").asText())
        .isEqualTo("Fakulteta za računalništvo in informatiko");
    assertThat(body.path("studyProgramme").asText()).isEqualTo("Računalništvo in informatika");
    assertThat(body.path("studentId").asText()).isEqualTo("63200001");

    Map<String, String> row = registrationRow(body.path("registrationId").asText());
    assertThat(row.get("type")).isEqualTo("STUDENT");
    assertThat(row.get("study_institution")).isEqualTo("Fakulteta za računalništvo in informatiko");
    assertThat(row.get("study_programme")).isEqualTo("Računalništvo in informatika");
    assertThat(row.get("student_id")).isEqualTo("63200001");
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
  void ac_002_02_missingRequiredStudentFieldIsRejected(String field) {
    String email = uniqueEmail("ac00202a");
    ObjectNode body = validStudent(email);
    body.remove(field);

    assertRejectedWithFieldError(register(body), field, "REQUIRED");
    assertThat(countRegistrationsByEmail(email)).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"studyInstitution", "studyProgramme", "studentId"})
  void ac_002_02_whitespaceOnlyStudentFieldIsRejected(String field) {
    String email = uniqueEmail("ac00202b");
    ObjectNode body = validStudent(email);
    body.put(field, "   ");

    assertRejectedWithFieldError(register(body), field, "REQUIRED");
    assertThat(countRegistrationsByEmail(email)).isZero();
  }

  @Test
  void ac_002_03_invalidEmailIsRejectedForStudents() {
    ObjectNode body = validStudent("x@example.si");
    body.put("email", "luka.student.example.si");

    assertRejectedWithFieldError(register(body), "email", "INVALID_EMAIL");
    assertThat(countRegistrationsByEmail("luka.student.example.si")).isZero();
  }

  @Test
  void ac_002_04_studentValuesAreTrimmedAndUnicodePreserving() {
    String email = uniqueEmail("ac00204");
    ObjectNode body = validStudent(email);
    body.put("studyProgramme", "  Računalništvo in informatika ");
    body.put("studyInstitution", " Fakulteta za elektrotehniko, Tržaška 25 ");
    body.put("firstName", " Žan ");

    Resp r = register(body);

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    Map<String, String> row = registrationRow(r.json().path("registrationId").asText());
    assertThat(row.get("study_programme")).isEqualTo("Računalništvo in informatika");
    assertThat(row.get("study_institution")).isEqualTo("Fakulteta za elektrotehniko, Tržaška 25");
    assertThat(row.get("first_name")).isEqualTo("Žan");
  }

  @Test
  void ac_002_05_mandatoryConsentIsRequiredForStudents() {
    String email = uniqueEmail("ac00205");
    ObjectNode body = validStudent(email);
    body.put("personalDataConsent", false);

    assertRejectedWithFieldError(register(body), "personalDataConsent", "CONSENT_REQUIRED");
    assertThat(countRegistrationsByEmail(email)).isZero();
  }

  @Test
  void ac_002_06_studentMaySelectActiveOptions() {
    String email = uniqueEmail("ac00206");
    Resp r = register(validStudent(email, "ws-sec", "ev-dinner"));

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    assertThat(optionIdsOf(r.json().path("registrationId").asText()))
        .containsExactly("ev-dinner", "ws-sec");
  }

  @Test
  void ac_002_07_unknownOptionIsRejectedForStudents() {
    String email = uniqueEmail("ac00207a");

    assertRejectedWithFieldError(
        register(validStudent(email, "nope")), "optionIds[0]", "UNKNOWN_OPTION");
    assertThat(countRegistrationsByEmail(email)).isZero();
  }

  @Test
  void ac_002_07_inactiveOptionIsRejectedForStudents() {
    String email = uniqueEmail("ac00207b");

    assertRejectedWithFieldError(
        register(validStudent(email, "ev-dinner", "meal-veg")), "optionIds[1]", "INACTIVE_OPTION");
    assertThat(countRegistrationsByEmail(email)).isZero();
  }

  @Test
  void ac_002_08_externalRegistrationDoesNotRequireStudentFields() {
    String email = uniqueEmail("ac00208a");
    ObjectNode body = validExternal(email);
    body.remove("studyInstitution");
    body.remove("studyProgramme");
    body.remove("studentId");

    assertThat(register(body).status()).isEqualTo(201);
  }

  @Test
  void ac_002_08_studentRegistrationDoesNotRequireOrganization() {
    String email = uniqueEmail("ac00208b");
    ObjectNode body = validStudent(email);
    body.remove("organization");

    Resp r = register(body);

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    assertThat(registrationRow(r.json().path("registrationId").asText()).get("organization"))
        .isNull();
  }

  @Test
  void ac_002_08_fieldOfTheOtherTypeIsNotAccepted() {
    String email = uniqueEmail("ac00208c");
    ObjectNode body = validStudent(email);
    body.put("organization", "Some company");

    assertRejectedWithFieldError(register(body), "organization", "FIELD_NOT_ALLOWED");
    assertThat(countRegistrationsByEmail(email)).isZero();
  }
}
