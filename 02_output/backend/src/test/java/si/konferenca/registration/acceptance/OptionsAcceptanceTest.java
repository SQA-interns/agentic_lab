package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.Api;
import tools.jackson.databind.JsonNode;

/** US-003 the active options from the configuration are offered through the API. */
class OptionsAcceptanceTest extends AcceptanceTest {

  @Test
  @DisplayName("AC-003-01 the API lists exactly the active options with id, name and category")
  void ac003_01_listsActiveOptionsOnly() {
    Api.Response response = api.get("/api/options");

    assertThat(response.status()).as(response.text()).isEqualTo(200);
    List<String> rows = new ArrayList<>();
    for (JsonNode o : response.json().get("options")) {
      rows.add(
          o.get("id").asString()
              + "|"
              + o.get("name").asString()
              + "|"
              + o.get("category").asString());
      assertThat(o.propertyNames()).containsExactlyInAnyOrder("id", "name", "category");
    }
    assertThat(rows)
        .containsExactly(
            "ws-ai|Delavnica umetne inteligence|workshop",
            "ev-dinner|Conference dinner|event",
            "meal-lunch|Kosilo, dan 1|meal",
            "other-tour|Ogled Ljubljane|other");
  }

  @Test
  @DisplayName("AC-003-01 the API lists the configured consents with text and required flag")
  void ac003_01_listsConsents() {
    Api.Response response = api.get("/api/options");

    assertThat(response.status()).isEqualTo(200);
    JsonNode consents = response.json().get("consents");
    assertThat(consents).hasSize(2);
    assertThat(consents.get(0).get("id").asString()).isEqualTo("privacy");
    assertThat(consents.get(0).get("text").asString())
        .isEqualTo(
            "I agree that the organizer processes my personal data to organise the conference.");
    assertThat(consents.get(0).get("required").asBoolean()).isTrue();
    assertThat(consents.get(1).get("id").asString()).isEqualTo("newsletter");
    assertThat(consents.get(1).get("required").asBoolean()).isFalse();
  }
}
