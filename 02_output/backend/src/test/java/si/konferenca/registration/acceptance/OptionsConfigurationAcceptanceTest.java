package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Payloads;
import si.konferenca.registration.acceptance.support.Startup;
import si.konferenca.registration.acceptance.support.TestEnvironment;
import tools.jackson.databind.JsonNode;

/** US-003 options change through the configuration file only (AR-04). */
class OptionsConfigurationAcceptanceTest {

  private static Map<String, String> withOptionsFile(String name) {
    Map<String, String> p = TestEnvironment.baseProperties();
    p.put("app.options-file", TestEnvironment.resource("acceptance/" + name).toUri().toString());
    return p;
  }

  @Test
  @DisplayName("AC-003-02 another options file changes the offered options and keeps the fields")
  void ac003_02_otherFileChangesOptions() {
    Startup.Outcome outcome = Startup.tryStart(withOptionsFile("alternative-options.json"));
    assertThat(outcome.started()).as("start failure: %s", outcome.failure()).isTrue();
    try {
      Api api = new Api(outcome.port());
      Api.Response options = api.get("/api/options");
      assertThat(options.status()).isEqualTo(200);
      List<String> ids = new ArrayList<>();
      for (JsonNode o : options.json().get("options")) {
        ids.add(o.get("id").asString());
      }
      assertThat(ids).containsExactly("ws-cloud", "meal-gala");
      assertThat(options.json().get("consents").get(0).get("text").asString())
          .isEqualTo("Alternative privacy wording.");

      Map<String, Object> payload = Payloads.external();
      payload.put("optionIds", List.of("ws-cloud", "meal-gala"));
      Api.Response accepted = api.register(payload);
      assertThat(accepted.status()).as(accepted.text()).isEqualTo(201);
      assertThat(accepted.json().get("options").get(1).get("name").asString())
          .isEqualTo("Gala večerja");

      Map<String, Object> inactive = Payloads.external();
      inactive.put("optionIds", List.of("ws-ai"));
      assertThat(api.register(inactive).status()).isEqualTo(400);

      Map<String, Object> studentWithOptions = Payloads.student();
      studentWithOptions.put("optionIds", List.of("ws-cloud"));
      studentWithOptions.put("consents", List.of("privacy"));
      assertThat(api.register(studentWithOptions).status()).isEqualTo(201);
    } finally {
      outcome.context().close();
    }
  }

  @ParameterizedTest(name = "AC-003-03 {0} refuses to start")
  @ValueSource(
      strings = {
        "invalid-duplicate-id.json",
        "invalid-unknown-category.json",
        "invalid-missing-name.json",
        "does-not-exist.json"
      })
  @DisplayName("AC-003-03 an invalid options configuration stops the backend from starting")
  void ac003_03_invalidConfigurationRefusesToStart(String file) {
    Map<String, String> p = TestEnvironment.baseProperties();
    p.put(
        "app.options-file",
        TestEnvironment.resource("acceptance/conference-options.json")
            .resolveSibling(file)
            .toUri()
            .toString());

    assertThat(Startup.starts(p)).isFalse();
  }

  @Test
  @DisplayName("AC-003-03 control: the valid test options file starts")
  void ac003_03_validConfigurationStarts() {
    assertThat(Startup.starts(TestEnvironment.baseProperties())).isTrue();
  }
}
