package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Fixtures;
import tools.jackson.databind.JsonNode;

/** US-001 external participant registration and US-002 student registration. */
class ParticipantRegistrationAcceptanceTest extends AcceptanceTestBase {

  @Test
  @DisplayName("AC-001-01 a valid external participant registration is accepted")
  void ac00101ValidExternalRegistrationIsAccepted() {
    String email = Fixtures.uniqueEmail();

    Api.Response r = api.register(Fixtures.external(email));

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    JsonNode body = r.json();
    assertThat(body.path("reference").asString()).matches("[0-9a-f-]{36}");
    assertThat(body.path("type").asString()).isEqualTo("EXTERNAL");
    assertThat(body.path("email").asString()).isEqualTo(email);
    assertThat(optionIds(body)).containsExactlyInAnyOrder("ws-secure-web", "meal-lunch-day1");
    assertThat(db.countRegistrations(email)).isEqualTo(1);
  }

  @ParameterizedTest(name = "AC-001-02 external field {0} empty or blank is rejected")
  @ValueSource(strings = {"firstName", "lastName", "email", "organization"})
  void ac00102MissingExternalFieldIsRejected(String field) {
    for (String value : new String[] {"", "   "}) {
      String email = Fixtures.uniqueEmail();
      Map<String, Object> request = Fixtures.external(email);
      request.put(field, value);

      Api.Response r = api.register(request);

      assertRejected(r, field);
      assertNothingStored(email);
    }
  }

  @Test
  @DisplayName("AC-001-02 an external registration without the organization property is rejected")
  void ac00102AbsentOrganizationIsRejected() {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.external(email);
    request.remove("organization");

    assertRejected(api.register(request), "organization");
    assertNothingStored(email);
  }

  @ParameterizedTest(name = "AC-001-03 invalid email \"{0}\" is rejected")
  @ValueSource(strings = {"ana", "ana@", "@example.si", "ana novak@example.si", "ana@example"})
  void ac00103InvalidEmailIsRejected(String invalid) {
    Map<String, Object> request = Fixtures.external(invalid);
    request.put("lastName", "Invalid-" + invalid.hashCode());

    Api.Response r = api.register(request);

    assertRejected(r, "email");
    assertThat(db.countRegistrations(invalid)).isZero();
  }

  @Test
  @DisplayName("AC-001-04 leading and trailing whitespace is removed before storage")
  void ac00104WhitespaceIsTrimmed() {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.external("  " + email + "  ");
    request.put("firstName", "  Ana  ");
    request.put("lastName", "\tNovak ");
    request.put("organization", " Institut Jožef Stefan ");

    Api.Response r = api.register(request);

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    String reference = r.json().path("reference").asString();
    Map<String, Object> row = db.registrationByReference(reference);
    assertThat(row).isNotNull();
    assertThat(row.get("first_name")).isEqualTo("Ana");
    assertThat(row.get("last_name")).isEqualTo("Novak");
    assertThat(row.get("email")).isEqualTo(email);
    assertThat(row.get("organization")).isEqualTo("Institut Jožef Stefan");
    assertThat(r.json().path("firstName").asString()).isEqualTo("Ana");
  }

  @Test
  @DisplayName("AC-001-05 Slovenian and other Unicode characters are stored unchanged")
  void ac00105UnicodeIsStoredUnchanged() {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.external(email);
    request.put("firstName", "Špela Đurđa");
    request.put("lastName", "Čučnik-Žagar");
    request.put("organization", "Fakulteta za računalništvo in informatiko, Łódź");

    Api.Response r = api.register(request);

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    Map<String, Object> row = db.registrationByReference(r.json().path("reference").asString());
    assertThat(row.get("first_name")).isEqualTo("Špela Đurđa");
    assertThat(row.get("last_name")).isEqualTo("Čučnik-Žagar");
    assertThat(row.get("organization"))
        .isEqualTo("Fakulteta za računalništvo in informatiko, Łódź");
  }

  @ParameterizedTest(name = "AC-001-06 selecting unknown or inactive option {0} is rejected")
  @ValueSource(strings = {"ws-legacy", "does-not-exist"})
  void ac00106UnknownOrInactiveOptionIsRejected(String optionId) {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.external(email);
    request.put("optionIds", List.of("ws-secure-web", optionId));

    assertRejected(api.register(request), "optionIds");
    assertNothingStored(email);
  }

  @Test
  @DisplayName("AC-001-07 a registration without the mandatory consent is rejected")
  void ac00107MissingMandatoryConsentIsRejected() {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.external(email);
    request.put("consentIds", List.of("photos"));

    assertRejected(api.register(request), "consentIds");
    assertNothingStored(email);
  }

  @Test
  @DisplayName("AC-001-07 an unknown consent id is rejected")
  void ac00107UnknownConsentIsRejected() {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.external(email);
    request.put("consentIds", List.of(Fixtures.MANDATORY_CONSENT, "not-a-consent"));

    assertRejected(api.register(request), "consentIds");
    assertNothingStored(email);
  }

  @Test
  @DisplayName("AC-002-01 a valid student registration is accepted")
  void ac00201ValidStudentRegistrationIsAccepted() {
    String email = Fixtures.uniqueEmail();

    Api.Response r = api.register(Fixtures.student(email));

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    assertThat(r.json().path("type").asString()).isEqualTo("STUDENT");
    Map<String, Object> row = db.registrationByReference(r.json().path("reference").asString());
    assertThat(row.get("type")).isEqualTo("STUDENT");
    assertThat(row.get("study_institution")).isEqualTo("Univerza v Ljubljani");
    assertThat(row.get("study_programme")).isEqualTo("Računalništvo in informatika");
    assertThat(row.get("student_id")).isEqualTo("63200001");
    assertThat(row.get("organization")).isNull();
  }

  @ParameterizedTest(name = "AC-002-02 student field {0} empty or blank is rejected")
  @ValueSource(
      strings = {
        "firstName",
        "lastName",
        "email",
        "studyInstitution",
        "studyProgramme",
        "studentId"
      })
  void ac00202MissingStudentFieldIsRejected(String field) {
    for (String value : new String[] {"", "  "}) {
      String email = Fixtures.uniqueEmail();
      Map<String, Object> request = Fixtures.student(email);
      request.put(field, value);

      assertRejected(api.register(request), field);
      assertNothingStored(email);
    }
  }

  @Test
  @DisplayName("AC-002-02 a student registration carrying an organization is rejected (BR-01)")
  void ac00202StudentWithExternalFieldIsRejected() {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.student(email);
    request.put("organization", "Some company");

    assertRejected(api.register(request), "organization");
    assertNothingStored(email);
  }

  private static List<String> optionIds(JsonNode confirmation) {
    return confirmation.path("options").valueStream().map(o -> o.path("id").asString()).toList();
  }
}
