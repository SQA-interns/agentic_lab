package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Payloads.external;
import static si.konferenca.registration.acceptance.support.Payloads.with;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.AppRunner;
import si.konferenca.registration.acceptance.support.Json;
import tools.jackson.databind.JsonNode;

/** US-003 Configurable conference options (`conference-config.schema.json`). */
class Us003ConferenceOptionsAcceptanceTest extends AcceptanceTestBase {

  private static JsonNode option(JsonNode config, String id) {
    for (JsonNode option : config.path("options")) {
      if (id.equals(option.path("id").asString())) {
        return option;
      }
    }
    throw new AssertionError("option " + id + " is not offered");
  }

  private static JsonNode formConfig(Api client) {
    Api.Response response = client.formConfig();
    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    return response.json();
  }

  @Test
  void AC_003_01_active_options_are_offered_grouped_by_category_with_display_names() {
    Api.Response response = api.formConfig();

    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    JsonNode config = response.json();
    assertThat(Json.strings(config.path("categories"), "id"))
        .containsExactly("WORKSHOP", "EVENT", "MEAL", "OTHER");
    assertThat(config.path("conferenceName").asString())
        .isEqualTo(AcceptanceEnvironment.CONFERENCE_NAME);
    JsonNode workshop = option(config, "ws-ai-research");
    assertThat(workshop.path("displayName").asString()).isEqualTo("Workshop: AI in research");
    assertThat(workshop.path("category").asString()).isEqualTo("WORKSHOP");
    assertThat(option(config, "ev-welcome").path("category").asString()).isEqualTo("EVENT");
    assertThat(option(config, "meal-lunch-day1").path("category").asString()).isEqualTo("MEAL");
    assertThat(option(config, "other-city-tour").path("displayName").asString())
        .isEqualTo("Ljubljana city tour");
    assertThat(config.path("categories").get(0).path("maxSelections").asInt()).isEqualTo(2);
    assertThat(Json.strings(config.path("consents"), "id"))
        .containsExactly(AcceptanceEnvironment.CONSENT_ID);
  }

  @Test
  void AC_003_02_inactive_option_is_not_offered() {
    JsonNode config = formConfig(api);

    assertThat(Json.strings(config.path("options"), "id")).doesNotContain("ws-legacy").hasSize(9);
  }

  @Test
  void AC_003_03_changed_configuration_is_offered_after_a_restart_without_code_change()
      throws IOException {
    Path changed = Files.createTempFile(AcceptanceEnvironment.WORK_DIR, "changed-", ".json");
    String original =
        Files.readString(AcceptanceEnvironment.CONFERENCE_CONFIG, StandardCharsets.UTF_8);
    String modified =
        original.replace(
            "{ \"id\": \"ev-welcome\", \"displayName\": \"Welcome reception\","
                + " \"category\": \"EVENT\", \"active\": true }",
            "{ \"id\": \"ev-welcome\", \"displayName\": \"Welcome reception\","
                + " \"category\": \"EVENT\", \"active\": false },\n"
                + "    { \"id\": \"ev-poster-night\", \"displayName\": \"Poster night\","
                + " \"category\": \"EVENT\", \"active\": true }");
    assertThat(modified).as("test fixture edit applied").isNotEqualTo(original);
    Files.writeString(changed, modified, StandardCharsets.UTF_8);
    Map<String, String> settings = AcceptanceEnvironment.baseSettings();
    settings.put("CONFERENCE_CONFIG_FILE", changed.toString());

    try (AppRunner app = AppRunner.start(settings)) {
      Api restarted = app.api();
      JsonNode config = formConfig(restarted);
      assertThat(Json.strings(config.path("options"), "id"))
          .contains("ev-poster-night")
          .doesNotContain("ev-welcome");

      assertAccepted(restarted.register(with(external(), "optionIds", List.of("ev-poster-night"))));
      assertFieldError(
          restarted.register(with(external(), "optionIds", List.of("ev-welcome"))),
          "optionIds",
          "INACTIVE_OPTION");
    }
  }

  @Test
  void AC_003_04_option_for_external_participants_only_states_its_availability() {
    JsonNode config = formConfig(api);

    assertThat(Json.strings(option(config, "ws-industry-lab").path("availableTo")))
        .containsExactly("EXTERNAL");
    assertThat(Json.strings(option(config, "ev-gala-dinner").path("availableTo")))
        .containsExactly("EXTERNAL");
    assertThat(Json.strings(option(config, "ws-open-data").path("availableTo")))
        .containsExactlyInAnyOrder("EXTERNAL", "STUDENT");
  }
}
