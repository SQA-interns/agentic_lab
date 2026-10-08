package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.student;
import static si.konferenca.registration.acceptance.support.Registrations.with;
import static si.konferenca.registration.acceptance.support.Registrations.without;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceStack;
import si.konferenca.registration.acceptance.support.Response;
import si.konferenca.registration.acceptance.support.RunningApp;
import tools.jackson.databind.JsonNode;

/** US-003 Configurable conference options: configuration file only, no code change. */
class Us003ConfigurableOptionsAcceptanceTest {

  private static final String CHANGED_OPTIONS =
      AcceptanceStack.OPTIONS_JSON
          .replace("\"Welcome reception\"", "\"Welcome reception (updated)\"")
          .replace(
              "{ \"id\": \"meal-dinner\", \"name\": \"Conference dinner (vegetarian option)\","
                  + " \"category\": \"MEAL\", \"active\": true }",
              "{ \"id\": \"meal-dinner\", \"name\": \"Conference dinner (vegetarian option)\","
                  + " \"category\": \"MEAL\", \"active\": false },\n"
                  + "    { \"id\": \"other-boat-trip\", \"name\": \"Izlet z ladjo po Ljubljanici\","
                  + " \"category\": \"OTHER\", \"active\": true }");

  @Test
  @DisplayName("AC-003-01 every active configured option is offered by name under its category")
  void ac003_01_configuredOptionsAreOffered() {
    Response response = AcceptanceStack.shared().api().getForm();

    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    JsonNode form = response.json();
    assertOption(form, "ws-testing", "Delavnica: testiranje programske opreme", "WORKSHOP");
    assertOption(form, "ev-reception", "Welcome reception", "EVENT");
    assertOption(form, "meal-lunch-day1", "Lunch, day 1", "MEAL");
    assertOption(form, "other-city-tour", "Ljubljana city tour", "OTHER");
    assertThat(form.path("options").size()).isEqualTo(7);
  }

  @Test
  @DisplayName("AC-003-02 a changed configuration takes effect after restart, without code change")
  void ac003_02_changedConfigurationIsReflectedAfterRestart() {
    assertThat(CHANGED_OPTIONS).contains("other-boat-trip").contains("(updated)");
    Path changed = AcceptanceStack.writeOptionsFile(CHANGED_OPTIONS);

    try (RunningApp app =
        AcceptanceStack.start(Map.of("CONFERENCE_OPTIONS_FILE", changed.toString()))) {
      Response form = app.api().getForm();
      assertThat(form.status()).as(form.toString()).isEqualTo(200);
      assertOption(form.json(), "other-boat-trip", "Izlet z ladjo po Ljubljanici", "OTHER");
      assertOption(form.json(), "ev-reception", "Welcome reception (updated)", "EVENT");
      assertThat(form.text()).doesNotContain("meal-dinner");

      Response added =
          app.api().register(with(external(), "optionIds", List.of("other-boat-trip")));
      assertThat(added.status()).as(added.toString()).isEqualTo(201);
      Response deactivated =
          app.api().register(with(external(), "optionIds", List.of("meal-dinner")));
      assertThat(deactivated.hasFieldError("optionIds", "inactive_option"))
          .as(deactivated.toString())
          .isTrue();
    }
  }

  @Test
  @DisplayName("AC-003-03 a changed configuration leaves the participant fields unchanged")
  void ac003_03_participantFieldsDoNotDependOnConfiguration() {
    Path changed = AcceptanceStack.writeOptionsFile(CHANGED_OPTIONS);

    try (RunningApp app =
        AcceptanceStack.start(Map.of("CONFERENCE_OPTIONS_FILE", changed.toString()))) {
      Response noOrganization = app.api().register(without(external(), "organization"));
      assertThat(noOrganization.hasFieldError("organization", "required"))
          .as(noOrganization.toString())
          .isTrue();
      Response noStudentId = app.api().register(without(student(), "studentId"));
      assertThat(noStudentId.hasFieldError("studentId", "required"))
          .as(noStudentId.toString())
          .isTrue();
      Response complete = app.api().register(with(student(), "optionIds", List.of("ws-testing")));
      assertThat(complete.status()).as(complete.toString()).isEqualTo(201);
    }
  }

  private static void assertOption(JsonNode form, String id, String name, String category) {
    JsonNode option = Us001ExternalRegistrationAcceptanceTest.optionById(form, id);
    assertThat(option.path("name").asString()).isEqualTo(name);
    assertThat(option.path("category").asString()).isEqualTo(category);
  }
}
