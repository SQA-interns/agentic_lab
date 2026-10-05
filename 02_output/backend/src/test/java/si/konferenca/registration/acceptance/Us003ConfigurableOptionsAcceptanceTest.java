package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_EVENT;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_EXTERNAL_ONLY;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_INACTIVE;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_MEAL;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_OTHER;
import static si.konferenca.registration.acceptance.support.Registrations.OPTION_WORKSHOP;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.withOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Database;
import tools.jackson.databind.JsonNode;

/** US-003 Configurable conference options, through the REST API. */
class Us003ConfigurableOptionsAcceptanceTest extends AcceptanceTestBase {

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    configure(registry, Map.of());
  }

  private JsonNode formConfig() {
    Api.Response response = api.getFormConfig();
    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(200);
    return response.json();
  }

  private JsonNode option(JsonNode formConfig, String id) {
    for (JsonNode option : formConfig.path("options")) {
      if (option.path("id").asString().equals(id)) {
        return option;
      }
    }
    return null;
  }

  @Test
  void ac003_01_activeOptionsOfAllCategoriesAreOfferedWithNameAndCategory() {
    JsonNode config = formConfig();
    List<String> categories = new ArrayList<>();
    config.path("options").forEach(o -> categories.add(o.path("category").asString()));
    assertThat(categories).contains("workshop", "event", "meal", "other");
    assertThat(option(config, OPTION_WORKSHOP).path("name").asString())
        .isEqualTo("Delavnica: testiranje");
    assertThat(option(config, OPTION_WORKSHOP).path("category").asString()).isEqualTo("workshop");
    assertThat(option(config, OPTION_EVENT).path("name").asString()).isEqualTo("Conference dinner");
    assertThat(option(config, OPTION_MEAL).path("category").asString()).isEqualTo("meal");
    assertThat(option(config, OPTION_OTHER).path("category").asString()).isEqualTo("other");
  }

  @Test
  void ac003_02_inactiveOptionIsNotOffered() {
    JsonNode config = formConfig();

    assertThat(config.path("options").findValuesAsString("id"))
        .isNotEmpty()
        .doesNotContain(OPTION_INACTIVE);
  }

  @Test
  void ac003_03_optionOfferedOnlyToExternalParticipantsIsMarkedSo() {
    JsonNode config = formConfig();

    List<String> externalOnly = new ArrayList<>();
    option(config, OPTION_EXTERNAL_ONLY)
        .path("offeredTo")
        .forEach(t -> externalOnly.add(t.asString()));
    assertThat(externalOnly).containsExactly("external");
    List<String> both = new ArrayList<>();
    option(config, OPTION_WORKSHOP).path("offeredTo").forEach(t -> both.add(t.asString()));
    assertThat(both).containsExactlyInAnyOrder("external", "student");
  }

  @Test
  void ac003_05_selectedOptionsAreStoredByTheirStableIdentifiers() {
    Api.Response response =
        api.register(withOptions(external(), OPTION_WORKSHOP, OPTION_EVENT, OPTION_OTHER));

    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(201);
    List<Map<String, Object>> rows =
        Database.rows(
            "SELECT option_id, category FROM registration_option WHERE registration_id = ?::uuid",
            response.json().path("registrationId").asString());
    assertThat(rows)
        .extracting(r -> r.get("option_id"))
        .containsExactlyInAnyOrder(OPTION_WORKSHOP, OPTION_EVENT, OPTION_OTHER);
  }
}
