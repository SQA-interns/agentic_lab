package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.withOptions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import si.konferenca.registration.RegistrationApplication;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Database;
import si.konferenca.registration.acceptance.support.JsonCopies;
import si.konferenca.registration.acceptance.support.Mailpit;
import si.konferenca.registration.acceptance.support.TestEnvironment;
import tools.jackson.databind.JsonNode;

/**
 * US-003, AC-003-04: options change through the configuration file and a restart, without a code
 * change, while the participant fields stay the same.
 */
class Us003OptionsChangeByConfigurationAcceptanceTest {

  private static final String CHANGED_CONFIG =
      """
      {
        "options": [
          { "id": "ws-testing", "name": "Delavnica: testiranje (prenovljena)", "category": "workshop", "active": true },
          { "id": "ws-new", "name": "Nova delavnica: varnost", "category": "workshop", "active": true },
          { "id": "ev-dinner", "name": "Conference dinner", "category": "event", "active": true },
          { "id": "meal-lunch", "name": "Kosilo", "category": "meal", "active": false },
          { "id": "other-tour", "name": "City tour", "category": "other", "active": true }
        ],
        "consents": [
          { "id": "data-processing", "text": "I agree to the processing of my personal data.", "mandatory": true }
        ]
      }
      """;

  private Path configFile;

  @BeforeEach
  void prepare() throws Exception {
    configFile = Files.createTempFile("acceptance-config-change", ".json");
    Files.write(configFile, TestEnvironment.resource("/acceptance/conference-config.json"));
    Database.removeFaults();
    Database.reset();
    JsonCopies.reset();
    Mailpit.deleteAll();
  }

  @AfterEach
  void cleanUp() throws Exception {
    Files.deleteIfExists(configFile);
  }

  private ConfigurableApplicationContext start() {
    Map<String, Object> properties = new HashMap<>(TestEnvironment.properties());
    properties.put("CONFERENCE_CONFIG_FILE", configFile.toAbsolutePath().toString());
    properties.put("server.port", "0");
    return new SpringApplicationBuilder(RegistrationApplication.class).properties(properties).run();
  }

  private static Api api(ConfigurableApplicationContext context) {
    return new Api(Integer.parseInt(context.getEnvironment().getProperty("local.server.port")));
  }

  private static Map<String, String> offeredNames(Api api) {
    Api.Response response = api.getFormConfig();
    assertThat(response.status()).as("body: %s", response.text()).isEqualTo(200);
    Map<String, String> names = new HashMap<>();
    for (JsonNode option : response.json().path("options")) {
      names.put(option.path("id").asString(), option.path("name").asString());
    }
    return names;
  }

  @Test
  void ac003_04_changedConfigurationChangesTheOptionsAfterRestartOnly() throws Exception {
    Map<String, String> before;
    try (ConfigurableApplicationContext context = start()) {
      before = offeredNames(api(context));
    }
    assertThat(before)
        .containsEntry("ws-testing", "Delavnica: testiranje")
        .containsKey("meal-lunch")
        .doesNotContainKey("ws-new");

    Files.writeString(configFile, CHANGED_CONFIG, StandardCharsets.UTF_8);

    try (ConfigurableApplicationContext context = start()) {
      Api api = api(context);
      Map<String, String> after = offeredNames(api);
      assertThat(after)
          .containsEntry("ws-testing", "Delavnica: testiranje (prenovljena)")
          .containsEntry("ws-new", "Nova delavnica: varnost")
          .doesNotContainKey("meal-lunch")
          .doesNotContainKey("ws-industry");

      Api.Response accepted = api.register(withOptions(external(), "ws-new"));
      assertThat(accepted.status()).as("body: %s", accepted.text()).isEqualTo(201);
      assertThat(accepted.json().path("firstName").asString()).isEqualTo("Ana");
      assertThat(accepted.json().path("options").findValuesAsString("name"))
          .containsExactly("Nova delavnica: varnost");

      var second = withOptions(external(), "meal-lunch");
      second.put("email", "another.participant@example.com");
      Api.Response deactivated = api.register(second);
      assertThat(deactivated.status()).isEqualTo(400);
      assertThat(Database.registrationCount()).isEqualTo(1);
    }
  }
}
