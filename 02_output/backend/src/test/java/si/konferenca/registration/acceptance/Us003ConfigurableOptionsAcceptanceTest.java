package si.konferenca.registration.acceptance;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.ApiClient;
import si.konferenca.registration.acceptance.support.Registrations;
import si.konferenca.registration.acceptance.support.RunningApp;
import si.konferenca.registration.acceptance.support.Workbook;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** US-003 Configurable conference options. */
class Us003ConfigurableOptionsAcceptanceTest extends AcceptanceTestBase {

  private static List<String> offered(ApiClient.Response config, String field) {
    List<String> values = new ArrayList<>();
    config.json().get("options").forEach(o -> values.add(o.get(field).asString()));
    return values;
  }

  private static JsonNode configuredOptions() throws IOException {
    return ApiClient.JSON.readTree(Files.readString(AcceptanceEnvironment.OPTIONS_FILE, UTF_8));
  }

  @Test
  @DisplayName("AC-003-01 active options are offered with name and category in all four groups")
  void ac003_01_activeOptionsAreOfferedByCategory() throws IOException {
    ApiClient.Response config = api.formConfig();

    assertThat(config.status()).isEqualTo(200);
    List<String> expectedIds = new ArrayList<>();
    for (JsonNode o : configuredOptions().get("options")) {
      if (o.get("active").asBoolean()) {
        expectedIds.add(o.get("id").asString());
      }
    }
    assertThat(offered(config, "id")).containsExactlyElementsOf(expectedIds);
    assertThat(offered(config, "category"))
        .containsOnly("workshop", "event", "meal", "other")
        .contains("workshop", "event", "meal", "other");
    JsonNode first = config.json().get("options").get(0);
    assertThat(first.get("name").asString()).isEqualTo("Delavnica: umetna inteligenca v praksi");
    assertThat(first.get("category").asString()).isEqualTo("workshop");
  }

  @Test
  @DisplayName("AC-003-02 inactive options are not offered")
  void ac003_02_inactiveOptionIsNotOffered() {
    ApiClient.Response config = api.formConfig();

    assertThat(config.status()).isEqualTo(200);
    assertThat(offered(config, "id")).doesNotContain("ws-legacy");
    assertThat(config.text()).doesNotContain("arhivirana tema");
  }

  @Test
  @DisplayName("AC-003-03 a changed option configuration is offered after a restart")
  void ac003_03_changedConfigurationIsOfferedAfterRestart() throws IOException {
    ObjectNode changed = (ObjectNode) configuredOptions();
    ArrayNode options = (ArrayNode) changed.get("options");
    for (JsonNode o : options) {
      ObjectNode option = (ObjectNode) o;
      switch (option.get("id").asString()) {
        case "ws-ai" -> option.put("name", "AI workshop (renamed)");
        case "ev-city-tour" -> option.put("active", false);
        default -> {}
      }
    }
    ObjectNode added = options.addObject();
    added.put("id", "ws-new-topic");
    added.put("name", "Nova delavnica");
    added.put("category", "workshop");
    added.put("active", true);
    Path file = Files.createTempFile("options-changed", ".json");
    Files.writeString(file, ApiClient.JSON.writeValueAsString(changed), UTF_8);
    Path jsonDir = Files.createTempDirectory("json-ac003-03");

    try (RunningApp app =
        RunningApp.start(
            AcceptanceEnvironment.propertiesFor(AcceptanceEnvironment.jdbcUrl(), jsonDir, file))) {
      ApiClient.Response config = app.api().formConfig();
      assertThat(config.status()).isEqualTo(200);
      assertThat(offered(config, "id")).contains("ws-new-topic").doesNotContain("ev-city-tour");
      assertThat(offered(config, "name")).contains("AI workshop (renamed)", "Nova delavnica");

      ObjectNode external = Registrations.withOptions(Registrations.external(), "ws-new-topic");
      assertThat(app.api().register(external).status()).isEqualTo(201);
      ObjectNode student = Registrations.withOptions(Registrations.student(), "ws-ai");
      assertThat(app.api().register(student).status()).isEqualTo(201);
      ObjectNode deactivated = Registrations.withOptions(Registrations.external(), "ev-city-tour");
      ApiClient.Response rejected = app.api().register(deactivated);
      assertThat(rejected.status()).isEqualTo(400);
      assertThat(rejected.text()).contains("INACTIVE_OPTION");
    }
  }

  @Test
  @DisplayName("AC-003-04 a stored registration keeps its option identifier after a rename")
  void ac003_04_storedRegistrationKeepsOptionIdentifierAfterRename() throws IOException {
    ObjectNode registration = Registrations.withOptions(Registrations.external(), "ws-ai");
    UUID id = idOf(registerAccepted(registration));

    ObjectNode renamed = (ObjectNode) configuredOptions();
    for (JsonNode o : renamed.get("options")) {
      if (o.get("id").asString().equals("ws-ai")) {
        ((ObjectNode) o).put("name", "Umetna inteligenca – preimenovano");
      }
    }
    Path file = Files.createTempFile("options-renamed", ".json");
    Files.writeString(file, ApiClient.JSON.writeValueAsString(renamed), UTF_8);
    Path jsonDir = Files.createTempDirectory("json-ac003-04");

    try (RunningApp app =
        RunningApp.start(
            AcceptanceEnvironment.propertiesFor(AcceptanceEnvironment.jdbcUrl(), jsonDir, file))) {
      ApiClient.Response export = app.api().export(app.api().organizerToken());
      assertThat(export.status()).isEqualTo(200);
      List<String> row =
          Workbook.parse(export.body()).rowFor(Registrations.email(registration)).orElseThrow();
      assertThat(Workbook.cell(row, "Options")).contains("[ws-ai]");
    }
    List<Map<String, Object>> options = database.options(id);
    assertThat(options)
        .singleElement()
        .satisfies(o -> assertThat(o.get("option_id")).isEqualTo("ws-ai"));
  }
}
