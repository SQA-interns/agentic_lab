package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.OptionsCatalog;
import si.konferenca.registration.domain.RegistrationType;

class OptionsFileLoaderTest {

  private static final String CONSENTS =
      "\"consents\":[{\"id\":\"dp\",\"text\":\"I agree.\",\"mandatory\":true}]";

  @TempDir Path dir;

  private Path file(String json) throws IOException {
    Path f = dir.resolve("options.json");
    Files.writeString(f, json, StandardCharsets.UTF_8);
    return f;
  }

  @Test
  void loadsOptionsLimitsAndConsents() throws IOException {
    OptionsCatalog c =
        OptionsFileLoader.load(
            file(
                "{\"options\":[{\"id\":\"ws-1\",\"name\":\"Delavnica č\",\"category\":\"WORKSHOP\","
                    + "\"active\":true},{\"id\":\"ev\",\"name\":\"E\",\"category\":\"EVENT\","
                    + "\"active\":false,\"registrationTypes\":[\"STUDENT\"]}],"
                    + "\"categoryLimits\":{\"MEAL\":1},"
                    + CONSENTS
                    + "}"));

    assertThat(c.options()).hasSize(2);
    assertThat(c.option("ws-1").orElseThrow().name()).isEqualTo("Delavnica č");
    assertThat(c.option("ws-1").orElseThrow().types())
        .containsExactlyInAnyOrder(RegistrationType.values());
    assertThat(c.option("ev").orElseThrow().types()).containsExactly(RegistrationType.STUDENT);
    assertThat(c.option("ev").orElseThrow().active()).isFalse();
    assertThat(c.limit(Category.MEAL)).contains(1);
    assertThat(c.limit(Category.EVENT)).isEmpty();
    assertThat(c.consent("dp").orElseThrow().mandatory()).isTrue();
    assertThat(c.offeredTo(RegistrationType.EXTERNAL)).extracting("id").containsExactly("ws-1");
  }

  @Test
  void loadsTheShippedConfiguration() {
    OptionsCatalog c = OptionsFileLoader.load(Path.of("config/options.json"));

    assertThat(c.options()).isNotEmpty();
    assertThat(c.consents()).anyMatch(x -> x.mandatory());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not json",
        "[]",
        "{\"options\":{}," + CONSENTS + "}",
        "{\"options\":[]," + CONSENTS + ",\"extra\":1}",
        "{\"options\":[{\"id\":\"A\",\"name\":\"n\",\"category\":\"WORKSHOP\",\"active\":true}],"
            + CONSENTS
            + "}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"n\",\"category\":\"WORKSHOP\",\"active\":true},"
            + "{\"id\":\"a\",\"name\":\"m\",\"category\":\"EVENT\",\"active\":true}],"
            + CONSENTS
            + "}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"n\",\"category\":\"PARTY\",\"active\":true}],"
            + CONSENTS
            + "}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"\",\"category\":\"MEAL\",\"active\":true}],"
            + CONSENTS
            + "}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"n\",\"category\":\"MEAL\",\"active\":\"yes\"}],"
            + CONSENTS
            + "}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"n\",\"category\":\"MEAL\",\"active\":true,"
            + "\"registrationTypes\":[]}],"
            + CONSENTS
            + "}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"n\",\"category\":\"MEAL\",\"active\":true,"
            + "\"registrationTypes\":[\"student\"]}],"
            + CONSENTS
            + "}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"n\",\"category\":\"MEAL\",\"active\":true,"
            + "\"registrationTypes\":[\"STUDENT\",\"STUDENT\"]}],"
            + CONSENTS
            + "}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"n\",\"category\":\"MEAL\",\"active\":true,"
            + "\"price\":3}],"
            + CONSENTS
            + "}",
        "{\"options\":[],\"categoryLimits\":{\"MEAL\":-1}," + CONSENTS + "}",
        "{\"options\":[],\"categoryLimits\":{\"PARTY\":1}," + CONSENTS + "}",
        "{\"options\":[],\"categoryLimits\":[]," + CONSENTS + "}",
        "{\"options\":[],\"consents\":[]}",
        "{\"options\":[]}",
        "{\"options\":[],\"consents\":[{\"id\":\"dp\",\"text\":\"t\",\"mandatory\":false}]}",
        "{\"options\":[],\"consents\":[{\"id\":\"dp\",\"text\":\"t\",\"mandatory\":true},"
            + "{\"id\":\"dp\",\"text\":\"u\",\"mandatory\":true}]}",
        "{\"options\":[],\"consents\":[{\"id\":\"dp\",\"text\":\"t\"}]}"
      })
  void rejectsInvalidConfiguration(String json) throws IOException {
    Path f = file(json);

    assertThatThrownBy(() -> OptionsFileLoader.load(f)).isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"options\":[1]," + CONSENTS + "}",
        "{\"options\":[],\"consents\":[1,{\"id\":\"dp\",\"text\":\"t\",\"mandatory\":true}]}",
        "{\"options\":[],\"consents\":[{\"id\":\"dp\",\"text\":\"t\",\"mandatory\":true,\"x\":1}]}",
        "{\"options\":[],\"consents\":[{\"id\":\"DP\",\"text\":\"t\",\"mandatory\":true}]}",
        "{\"options\":[],\"consents\":[{\"id\":\"dp\",\"text\":\"t\",\"mandatory\":true},"
            + "{\"id\":\"ph\",\"text\":\"u\",\"mandatory\":\"no\"}]}",
        "{\"options\":[],\"consents\":{\"id\":\"dp\"}}",
        "5"
      })
  void rejectsASingleViolatedConsentOrOptionRule(String json) throws IOException {
    Path f = file(json);

    assertThatThrownBy(() -> OptionsFileLoader.load(f))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Invalid options file");
  }

  @Test
  void acceptsValuesExactlyAtTheLimits() throws IOException {
    OptionsCatalog c =
        OptionsFileLoader.load(
            file(
                "{\"options\":[{\"id\":\"a\",\"name\":\""
                    + "n".repeat(200)
                    + "\",\"category\":\"MEAL\",\"active\":true}],\"categoryLimits\":{\"MEAL\":0},"
                    + "\"consents\":[{\"id\":\"dp\",\"text\":\""
                    + "t".repeat(1000)
                    + "\",\"mandatory\":true}]}"));

    assertThat(c.limit(Category.MEAL)).contains(0);
    assertThat(c.option("a").orElseThrow().name()).hasSize(200);
  }

  @Test
  void rejectsMissingFile() {
    assertThatThrownBy(() -> OptionsFileLoader.load(dir.resolve("missing.json")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cannot be read");
  }

  @Test
  void rejectsOverlongNameAndConsent() throws IOException {
    Path longName =
        file(
            "{\"options\":[{\"id\":\"a\",\"name\":\""
                + "n".repeat(201)
                + "\",\"category\":\"MEAL\",\"active\":true}],"
                + CONSENTS
                + "}");
    assertThatThrownBy(() -> OptionsFileLoader.load(longName))
        .hasMessageContaining("longer than 200");

    Path longConsent =
        file(
            "{\"options\":[],\"consents\":[{\"id\":\"dp\",\"text\":\""
                + "t".repeat(1001)
                + "\",\"mandatory\":true}]}");
    assertThatThrownBy(() -> OptionsFileLoader.load(longConsent))
        .hasMessageContaining("longer than 1000");
  }
}
