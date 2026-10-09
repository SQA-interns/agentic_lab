package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Api.array;
import static si.konferenca.registration.acceptance.support.Api.assertFieldError;
import static si.konferenca.registration.acceptance.support.Api.assertNothingStoredFor;
import static si.konferenca.registration.acceptance.support.Api.external;
import static si.konferenca.registration.acceptance.support.Api.jsonCopies;
import static si.konferenca.registration.acceptance.support.Api.register;
import static si.konferenca.registration.acceptance.support.Api.student;
import static si.konferenca.registration.acceptance.support.Api.uniqueEmail;

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

/** US-003 Configurable conference options: the same build run with a changed options file. */
class Us003ConfigurableOptionsAcceptanceTest {

  private static Backend changed;

  @BeforeAll
  static void start() {
    changed =
        Backend.builder().conferenceConfigResource("/acceptance/conference-changed.json").start();
  }

  @AfterAll
  static void stop() {
    changed.close();
  }

  private static JsonNode form(String type) {
    Response response = Api.get(changed.url("/api/registration-form/" + type));
    assertThat(response.status()).isEqualTo(200);
    return response.json();
  }

  private static List<String> values(JsonNode array, String property) {
    List<String> values = new ArrayList<>();
    array.forEach(n -> values.add(n.get(property).asString()));
    return values;
  }

  private static ObjectNode withChangedConsent(ObjectNode body) {
    body.set("consentIds", array("privacy"));
    return body;
  }

  @Test
  void AC_003_01_configuredOptionIsOfferedUnderItsCategoryForItsTypes() {
    JsonNode externalForm = form("external");
    JsonNode studentForm = form("student");

    assertThat(values(externalForm.get("categories").get(0).get("options"), "id"))
        .containsExactly("ws-new-topic");
    assertThat(values(externalForm.get("categories").get(0).get("options"), "name"))
        .containsExactly("Nova delavnica: varnost");
    assertThat(values(externalForm.get("categories").get(3).get("options"), "id")).isEmpty();
    assertThat(values(studentForm.get("categories").get(3).get("options"), "id"))
        .containsExactly("other-hike");

    ObjectNode body = withChangedConsent(student(uniqueEmail()));
    body.set("optionIds", array("ws-new-topic", "other-hike"));
    Response response = register(changed, body);
    assertThat(response.status()).as(response.text()).isEqualTo(201);
  }

  @Test
  void AC_003_02_optionMarkedInactiveIsNotOfferedAndIsRejected() {
    assertThat(values(form("external").get("categories").get(0).get("options"), "id"))
        .doesNotContain("ws-testing-ai");

    String email = uniqueEmail();
    ObjectNode body = withChangedConsent(external(email));
    body.set("optionIds", array("ws-testing-ai"));
    int copies = jsonCopies(changed).size();

    Response response = register(changed, body);

    assertFieldError(response, 400, "optionIds", "OPTION_NOT_AVAILABLE");
    assertNothingStoredFor(changed, email, copies);
  }

  @Test
  void AC_003_03_changedOptionsKeepTheFixedFieldsOfBothForms() {
    assertThat(values(form("external").get("fields"), "name"))
        .containsExactly("firstName", "lastName", "email", "organization");
    assertThat(values(form("student").get("fields"), "name"))
        .containsExactly(
            "firstName", "lastName", "email", "studyInstitution", "studyProgramme", "studentId");
  }

  @Test
  void AC_003_04_configuredConsentsAreShownWithTheirWording() {
    for (String type : List.of("external", "student")) {
      JsonNode consents = form(type).get("consents");
      assertThat(values(consents, "id")).containsExactly("privacy");
      assertThat(consents.get(0).get("text").asString())
          .isEqualTo(
              "Strinjam se z obdelavo osebnih podatkov za organizacijo konference"
                  + " (hramba do 12 mesecev po konferenci).");
      assertThat(consents.get(0).get("mandatory").asBoolean()).isTrue();
    }
  }
}
