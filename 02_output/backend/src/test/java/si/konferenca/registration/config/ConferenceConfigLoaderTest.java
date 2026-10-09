package si.konferenca.registration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceCatalogue;
import si.konferenca.registration.domain.RegistrationType;

class ConferenceConfigLoaderTest {

  @TempDir Path dir;

  private Path file(String json) throws IOException {
    Path file = dir.resolve("conference.json");
    Files.writeString(file, json);
    return file;
  }

  private static String option(String id, String extra) {
    return "{\"id\":\""
        + id
        + "\",\"name\":\"N\",\"category\":\"MEAL\",\"active\":true,\"availableTo\":[\"STUDENT\"]"
        + extra
        + "}";
  }

  private static String config(String options, String consents) {
    return "{\"options\":[" + options + "],\"consents\":[" + consents + "]}";
  }

  private static final String CONSENT = "{\"id\":\"privacy\",\"text\":\"I agree.\"}";

  @Test
  void ar04_theShippedExampleLoads() {
    ConferenceCatalogue catalogue =
        ConferenceConfigLoader.load(
            Path.of("../docs/02_contracts/examples/conference-config.example.json"));

    assertThat(catalogue.maxSelections(Category.EVENT)).isEqualTo(3);
    assertThat(catalogue.selectableOptions(RegistrationType.STUDENT, Category.WORKSHOP)).hasSize(1);
    assertThat(catalogue.consents()).hasSize(1);
  }

  @Test
  void ar04_theLocalConfigurationLoads() {
    assertThat(ConferenceConfigLoader.load(Path.of("../config/conference.json")).consents())
        .isNotEmpty();
  }

  @Test
  void d15_consentIsMandatoryByDefault() throws IOException {
    ConferenceCatalogue catalogue =
        ConferenceConfigLoader.load(file(config(option("m", ""), CONSENT)));

    assertThat(catalogue.consents().getFirst().mandatory()).isTrue();
  }

  @Test
  void duplicateOptionIdIsRefused() throws IOException {
    Path file = file(config(option("m", "") + "," + option("m", ""), CONSENT));

    assertThatThrownBy(() -> ConferenceConfigLoader.load(file))
        .hasMessageContaining("repeats id m");
  }

  @Test
  void unknownPropertyIsRefused() throws IOException {
    Path file = file(config(option("m", ",\"price\":3"), CONSENT));

    assertThatThrownBy(() -> ConferenceConfigLoader.load(file)).hasMessageContaining("price");
  }

  @Test
  void unknownCategoryIsRefused() throws IOException {
    Path file = file(config(option("m", "").replace("MEAL", "PARTY"), CONSENT));

    assertThatThrownBy(() -> ConferenceConfigLoader.load(file)).hasMessageContaining("category");
  }

  @Test
  void invalidIdentifierIsRefused() throws IOException {
    Path file = file(config(option("Bad Id", ""), CONSENT));

    assertThatThrownBy(() -> ConferenceConfigLoader.load(file)).hasMessageContaining(".id");
  }

  @Test
  void emptyAvailabilityIsRefused() throws IOException {
    Path file = file(config(option("m", "").replace("[\"STUDENT\"]", "[]"), CONSENT));

    assertThatThrownBy(() -> ConferenceConfigLoader.load(file)).hasMessageContaining("availableTo");
  }

  @Test
  void missingConsentsAreRefused() throws IOException {
    Path file = file(config(option("m", ""), ""));

    assertThatThrownBy(() -> ConferenceConfigLoader.load(file)).hasMessageContaining("consents");
  }

  @Test
  void negativeOrUnknownLimitIsRefused() throws IOException {
    Path negative =
        file(
            "{\"maxSelectionsPerCategory\":{\"MEAL\":-1},\"options\":[],\"consents\":["
                + CONSENT
                + "]}");
    assertThatThrownBy(() -> ConferenceConfigLoader.load(negative)).hasMessageContaining("MEAL");

    Path unknown =
        file(
            "{\"maxSelectionsPerCategory\":{\"SPA\":1},\"options\":[],\"consents\":["
                + CONSENT
                + "]}");
    assertThatThrownBy(() -> ConferenceConfigLoader.load(unknown)).hasMessageContaining("SPA");
  }

  @Test
  void invalidJsonOrMissingFileIsRefused() throws IOException {
    Path broken = file("{not json");
    assertThatThrownBy(() -> ConferenceConfigLoader.load(broken)).hasMessageContaining("JSON");
    assertThatThrownBy(() -> ConferenceConfigLoader.load(dir.resolve("missing.json")))
        .isInstanceOf(IllegalStateException.class);
  }
}
