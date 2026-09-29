package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import si.konferenca.registration.RegistrationApplication;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.TestInfrastructure;

/** US-003, AR-04: options change through configuration and a restart, without code changes. */
class OptionsConfigurationChangeAcceptanceTest {

  private static final String BEFORE =
      """
      { "options": [
          { "id": "a-workshop", "name": "Workshop A", "category": "WORKSHOP", "active": true },
          { "id": "b-event", "name": "Event B", "category": "EVENT", "active": false } ],
        "consents": [ { "id": "data-processing", "text": "I agree.", "mandatory": true } ] }
      """;

  private static final String AFTER =
      """
      { "options": [
          { "id": "a-workshop", "name": "Workshop A", "category": "WORKSHOP", "active": false },
          { "id": "b-event", "name": "Event B", "category": "EVENT", "active": true },
          { "id": "c-meal", "name": "Meal C", "category": "MEAL", "active": true } ],
        "consents": [ { "id": "data-processing", "text": "I agree.", "mandatory": true } ] }
      """;

  @Test
  @DisplayName("AC-003-02 changed options configuration is visible after a restart")
  void ac00302ConfigurationChangeIsVisibleAfterRestart() throws IOException {
    Path optionsFile = TestInfrastructure.writeOptionsFile(BEFORE);

    assertThat(activeOptionIds(optionsFile)).containsExactly("a-workshop");

    Files.writeString(optionsFile, AFTER, StandardCharsets.UTF_8);

    assertThat(activeOptionIds(optionsFile)).containsExactly("b-event", "c-meal");
  }

  private static List<String> activeOptionIds(Path optionsFile) {
    Map<String, Object> props = new HashMap<>(TestInfrastructure.defaultProperties());
    props.put("app.options-file", optionsFile.toString());
    props.put("app.json-copy-dir", TestInfrastructure.newTempDir("copies").toString());
    props.put("server.port", "0");
    try (ConfigurableApplicationContext app =
        new SpringApplicationBuilder(RegistrationApplication.class).properties(props).run()) {
      int port = Integer.parseInt(app.getEnvironment().getProperty("local.server.port"));
      Api.Response r = new Api(port).get("/api/options?type=EXTERNAL");
      assertThat(r.status()).as(r.text()).isEqualTo(200);
      return ConferenceOptionsAcceptanceTest.ids(r.json().path("options"));
    }
  }
}
