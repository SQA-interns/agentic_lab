package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Checks.assertFieldError;
import static si.konferenca.registration.acceptance.support.Checks.assertNothingStored;
import static si.konferenca.registration.acceptance.support.Payloads.EVENT;
import static si.konferenca.registration.acceptance.support.Payloads.MEAL;
import static si.konferenca.registration.acceptance.support.Payloads.OTHER;
import static si.konferenca.registration.acceptance.support.Payloads.WORKSHOP;
import static si.konferenca.registration.acceptance.support.Payloads.student;
import static si.konferenca.registration.acceptance.support.Payloads.with;
import static si.konferenca.registration.acceptance.support.Payloads.without;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Database;
import tools.jackson.databind.JsonNode;

/** US-002 student registration through the REST API. */
class StudentRegistrationAcceptanceTest extends AcceptanceTest {

  @Test
  @DisplayName("AC-002-01 a valid student registration with options of every category is accepted")
  void ac002_01_acceptsValidStudentRegistration() {
    Map<String, Object> payload = student();
    String email = (String) payload.get("email");

    Api.Response response = api.register(payload);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    JsonNode body = response.json();
    assertThat(body.get("type").asString()).isEqualTo("STUDENT");
    assertThat(body.get("studyInstitution").asString()).isEqualTo("Univerza v Ljubljani");
    assertThat(body.get("studyProgramme").asString()).isEqualTo("Računalništvo in informatika");
    assertThat(body.get("studentId").asString()).isEqualTo("63210001");
    assertThat(body.has("organization")).isFalse();
    List<String> categories = new ArrayList<>();
    body.get("options").forEach(o -> categories.add(o.get("category").asString()));
    assertThat(categories).containsExactly("workshop", "event", "meal", "other");

    Map<String, Object> row = Database.registrationByEmail(email);
    assertThat(row).isNotNull();
    assertThat(row.get("type")).isEqualTo("STUDENT");
    assertThat(row.get("study_institution")).isEqualTo("Univerza v Ljubljani");
    assertThat(row.get("study_programme")).isEqualTo("Računalništvo in informatika");
    assertThat(row.get("student_id")).isEqualTo("63210001");
    assertThat(row.get("organization")).isNull();
    List<String> stored = new ArrayList<>();
    Database.options(row.get("id")).forEach(o -> stored.add((String) o.get("option_id")));
    assertThat(stored).containsExactly(WORKSHOP, EVENT, MEAL, OTHER);
  }

  static Stream<Arguments> missingStudentFields() {
    List<Arguments> args = new ArrayList<>();
    for (String field :
        List.of(
            "firstName", "lastName", "email", "studyInstitution", "studyProgramme", "studentId")) {
      args.add(Arguments.of(field, null));
      args.add(Arguments.of(field, ""));
      args.add(Arguments.of(field, "  "));
    }
    return args.stream();
  }

  @ParameterizedTest(name = "AC-002-02 {0} = [{1}] is rejected")
  @MethodSource("missingStudentFields")
  @DisplayName("AC-002-02 a missing, empty or blank student field is rejected")
  void ac002_02_rejectsMissingStudentField(String field, String value) {
    Map<String, Object> payload = student();
    String email = (String) payload.get("email");
    Map<String, Object> sent =
        value == null ? without(payload, field) : with(payload, field, value);

    Api.Response response = api.register(sent);

    assertFieldError(response, 400, field);
    assertNothingStored(email, jsonDir());
  }

  @Test
  @DisplayName("AC-002-02 a student registration containing organization is rejected")
  void ac002_02_rejectsOrganizationOnStudentForm() {
    Map<String, Object> payload = with(student(), "organization", "Podjetje d.o.o.");
    String email = (String) payload.get("email");

    Api.Response response = api.register(payload);

    assertFieldError(response, 400, "organization");
    assertNothingStored(email, jsonDir());
  }

  @Test
  @DisplayName("AC-002-02 an unknown registration type is rejected")
  void ac002_02_rejectsUnknownType() {
    Map<String, Object> payload = with(student(), "type", "VISITOR");
    String email = (String) payload.get("email");

    Api.Response response = api.register(payload);

    assertThat(response.status()).isEqualTo(400);
    assertNothingStored(email, jsonDir());
  }
}
