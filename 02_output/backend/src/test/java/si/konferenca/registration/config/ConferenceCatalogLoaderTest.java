package si.konferenca.registration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceCatalog;
import si.konferenca.registration.domain.RegistrationType;

class ConferenceCatalogLoaderTest {

  private static final String CONSENTS = "\"consents\":[{\"id\":\"c\",\"text\":\"I agree\"}]";

  @TempDir Path dir;

  private ConferenceCatalog load(String json) throws IOException {
    Path file = dir.resolve("config.json");
    Files.writeString(file, json);
    return new ConferenceCatalogLoader().load(file.toString());
  }

  @Test
  void loadsTheBundledExampleWhenNoFileIsConfigured() {
    ConferenceCatalog catalog = new ConferenceCatalogLoader().load("");

    assertThat(catalog.activeOptions()).hasSize(9);
    assertThat(catalog.limit(Category.WORKSHOP)).contains(2);
    assertThat(catalog.limit(Category.MEAL)).isEmpty();
    assertThat(
            catalog.option("ws-industry-lab").orElseThrow().availableTo(RegistrationType.STUDENT))
        .isFalse();
  }

  @Test
  void loadsAMinimalFileWithDefaults() throws IOException {
    ConferenceCatalog catalog =
        load(
            "{"
                + CONSENTS
                + ",\"options\":[{\"id\":\"a\",\"displayName\":\"A\",\"category\":\"OTHER\","
                + "\"active\":true}]}");

    assertThat(catalog.option("a").orElseThrow().availableTo(RegistrationType.STUDENT)).isTrue();
    assertThat(catalog.option("a").orElseThrow().availableTo(RegistrationType.EXTERNAL)).isTrue();
    assertThat(catalog.consents()).singleElement().extracting("text").isEqualTo("I agree");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[]",
        "{\"options\":[]}",
        "{\"consents\":[],\"options\":[]}",
        "{" + CONSENTS + ",\"options\":[],\"extra\":1}",
        "{" + CONSENTS + ",\"options\":{}}",
        "{"
            + CONSENTS
            + ",\"options\":[{\"id\":\"Bad Id\",\"displayName\":\"A\","
            + "\"category\":\"OTHER\",\"active\":true}]}",
        "{"
            + CONSENTS
            + ",\"options\":[{\"id\":\"a\",\"displayName\":\"\","
            + "\"category\":\"OTHER\",\"active\":true}]}",
        "{"
            + CONSENTS
            + ",\"options\":[{\"id\":\"a\",\"displayName\":\"A\","
            + "\"category\":\"PARTY\",\"active\":true}]}",
        "{"
            + CONSENTS
            + ",\"options\":[{\"id\":\"a\",\"displayName\":\"A\","
            + "\"category\":\"OTHER\",\"active\":\"yes\"}]}",
        "{"
            + CONSENTS
            + ",\"options\":[{\"id\":\"a\",\"displayName\":\"A\","
            + "\"category\":\"OTHER\",\"active\":true,\"availableTo\":[]}]}",
        "{"
            + CONSENTS
            + ",\"options\":[{\"id\":\"a\",\"displayName\":\"A\","
            + "\"category\":\"OTHER\",\"active\":true,\"availableTo\":[\"STUDENT\",\"STUDENT\"]}]}",
        "{"
            + CONSENTS
            + ",\"options\":[{\"id\":\"a\",\"displayName\":\"A\","
            + "\"category\":\"OTHER\",\"active\":true,\"availableTo\":[\"GUEST\"]}]}",
        "{"
            + CONSENTS
            + ",\"options\":[{\"id\":\"a\",\"displayName\":\"A\",\"category\":\"OTHER\","
            + "\"active\":true},{\"id\":\"a\",\"displayName\":\"B\",\"category\":\"OTHER\","
            + "\"active\":false}]}",
        "{" + CONSENTS + ",\"categoryLimits\":{\"WORKSHOP\":0},\"options\":[]}",
        "{" + CONSENTS + ",\"categoryLimits\":{\"WORKSHOP\":101},\"options\":[]}",
        "{" + CONSENTS + ",\"categoryLimits\":{\"PARTY\":1},\"options\":[]}",
        "{\"consents\":[{\"id\":\"c\",\"text\":\"x\"},{\"id\":\"c\",\"text\":\"y\"}],\"options\":[]}",
        "{\"consents\":[{\"id\":\"c\"}],\"options\":[]}",
        "not json"
      })
  void refusesAnInvalidConfiguration(String json) {
    assertThatThrownBy(() -> load(json)).isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  void refusesAMissingFileWithoutNamingItsContent() {
    assertThatThrownBy(() -> new ConferenceCatalogLoader().load(dir.resolve("none").toString()))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("CONFERENCE_CONFIG_FILE");
  }

  @Test
  void acceptsLimitsAtTheBoundaries() throws IOException {
    ConferenceCatalog catalog =
        load("{" + CONSENTS + ",\"categoryLimits\":{\"MEAL\":1,\"EVENT\":100},\"options\":[]}");

    assertThat(catalog.limit(Category.MEAL)).contains(1);
    assertThat(catalog.limit(Category.EVENT)).contains(100);
  }
}
