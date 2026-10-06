package si.konferenca.registration.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import si.konferenca.registration.domain.ConferenceCatalog;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.RegistrationType;

class ConferenceConfigFileTest {

  @TempDir Path dir;

  private Path write(String json) throws IOException {
    Path file = dir.resolve("config.json");
    Files.writeString(file, json, StandardCharsets.UTF_8);
    return file;
  }

  private static String option(String extra) {
    return "{\"id\":\"ws-a\",\"name\":\"Delavnica č\",\"category\":\"workshop\",\"active\":true"
        + extra
        + "}";
  }

  private static String config(String options, String consents) {
    return "{\"options\":[" + options + "],\"consents\":[" + consents + "]}";
  }

  private static final String CONSENT = "{\"id\":\"data\",\"text\":\"I agree\",\"mandatory\":true}";

  @Test
  void readsOptionsAndConsentsWithDefaultAudience() throws IOException {
    ConferenceCatalog catalog =
        ConferenceConfigFile.load(
            write(
                config(
                    option("")
                        + ","
                        + "{\"id\":\"m\",\"name\":\"Kosilo\",\"category\":\"meal\",\"active\":false,\"offeredTo\":[\"student\"]}",
                    CONSENT)));

    assertThat(catalog.options()).hasSize(2);
    assertThat(catalog.options().get(0).name()).isEqualTo("Delavnica č");
    assertThat(catalog.options().get(0).category()).isEqualTo(OptionCategory.WORKSHOP);
    assertThat(catalog.options().get(0).offeredTo())
        .containsExactlyInAnyOrder(RegistrationType.values());
    assertThat(catalog.options().get(1).active()).isFalse();
    assertThat(catalog.options().get(1).offeredTo()).containsExactly(RegistrationType.STUDENT);
    assertThat(catalog.consents().get(0).mandatory()).isTrue();
  }

  @Test
  void emptyListsAreAllowed() throws IOException {
    assertThat(ConferenceConfigFile.load(write(config("", ""))).options()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "not json|cannot be read as JSON",
        "[]|the file must be an object",
        "{\"options\":[]}|the file has no consents",
        "{\"options\":[],\"consents\":[],\"x\":1}|unknown property x",
        "{\"options\":{},\"consents\":[]}|options must be an array",
        "{\"options\":[1],\"consents\":[]}|options[0] must be an object",
      })
  void invalidStructureStopsStartup(String json, String message) throws IOException {
    Path file = write(json);
    assertThatThrownBy(() -> ConferenceConfigFile.load(file))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("CONFERENCE_CONFIG_FILE is invalid")
        .hasMessageContaining(message);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "{\"id\":\"WS\",\"name\":\"n\",\"category\":\"workshop\",\"active\":true}|options[0].id",
        "{\"id\":\"-ws\",\"name\":\"n\",\"category\":\"workshop\",\"active\":true}|options[0].id",
        "{\"id\":7,\"name\":\"n\",\"category\":\"workshop\",\"active\":true}|options[0].id",
        "{\"id\":\"ws\",\"name\":\"\",\"category\":\"workshop\",\"active\":true}|options[0].name",
        "{\"id\":\"ws\",\"name\":\"n\",\"category\":\"party\",\"active\":true}|options[0].category",
        "{\"id\":\"ws\",\"name\":\"n\",\"category\":\"workshop\",\"active\":\"yes\"}|options[0].active",
        "{\"id\":\"ws\",\"name\":\"n\",\"category\":\"workshop\",\"active\":true,\"offeredTo\":[]}|offeredTo must be a non-empty array",
        "{\"id\":\"ws\",\"name\":\"n\",\"category\":\"workshop\",\"active\":true,\"offeredTo\":[\"guest\"]}|offeredTo has an unknown type",
        "{\"id\":\"ws\",\"name\":\"n\",\"category\":\"workshop\",\"active\":true,\"offeredTo\":[\"student\",\"student\"]}|offeredTo repeats a type",
        "{\"id\":\"ws\",\"name\":\"n\",\"category\":\"workshop\"}|options[0] has no active",
        "{\"id\":\"ws\",\"name\":\"n\",\"category\":\"workshop\",\"active\":true,\"price\":3}|unknown property price",
      })
  void invalidOptionStopsStartup(String option, String message) throws IOException {
    Path file = write(config(option, CONSENT));
    assertThatThrownBy(() -> ConferenceConfigFile.load(file)).hasMessageContaining(message);
  }

  @Test
  void limitsOfNamesTextsAndIdsAreChecked() throws IOException {
    String longName = "n".repeat(201);
    Path tooLongName =
        write(
            config(
                "{\"id\":\"ws\",\"name\":\""
                    + longName
                    + "\",\"category\":\"workshop\",\"active\":true}",
                CONSENT));
    assertThatThrownBy(() -> ConferenceConfigFile.load(tooLongName))
        .hasMessageContaining("1 to 200");

    Path longestId =
        write(
            config(
                "{\"id\":\""
                    + "a".repeat(64)
                    + "\",\"name\":\""
                    + "n".repeat(200)
                    + "\",\"category\":\"workshop\",\"active\":true}",
                CONSENT));
    assertThat(ConferenceConfigFile.load(longestId).options()).hasSize(1);

    Path tooLongId =
        write(
            config(
                "{\"id\":\""
                    + "a".repeat(65)
                    + "\",\"name\":\"n\",\"category\":\"workshop\",\"active\":true}",
                CONSENT));
    assertThatThrownBy(() -> ConferenceConfigFile.load(tooLongId))
        .hasMessageContaining("options[0].id");

    Path longConsent =
        write(
            config("", "{\"id\":\"c\",\"text\":\"" + "t".repeat(2001) + "\",\"mandatory\":true}"));
    assertThatThrownBy(() -> ConferenceConfigFile.load(longConsent))
        .hasMessageContaining("consents[0].text");
  }

  private static String consents(int count) {
    StringBuilder consents = new StringBuilder();
    for (int i = 0; i < count; i++) {
      consents
          .append(i == 0 ? "" : ",")
          .append("{\"id\":\"c")
          .append(i)
          .append("\",\"text\":\"t\",\"mandatory\":false}");
    }
    return consents.toString();
  }

  @Test
  void tooManyEntriesAreRejected() throws IOException {
    Path file = write(config("", consents(21)));
    assertThatThrownBy(() -> ConferenceConfigFile.load(file)).hasMessageContaining("at most 20");
  }

  @Test
  void theMaximumNumberOfEntriesIsAllowed() throws IOException {
    assertThat(ConferenceConfigFile.load(write(config("", consents(20)))).consents()).hasSize(20);
  }

  @Test
  void consentMustBeAnObjectWithKnownProperties() throws IOException {
    Path notObject = write(config("", "\"data\""));
    assertThatThrownBy(() -> ConferenceConfigFile.load(notObject))
        .hasMessageContaining("consents[0] must be an object");
    Path extra = write(config("", "{\"id\":\"d\",\"text\":\"t\",\"mandatory\":true,\"x\":1}"));
    assertThatThrownBy(() -> ConferenceConfigFile.load(extra))
        .hasMessageContaining("unknown property x");
  }

  @Test
  void repeatedIdsAreRejected() throws IOException {
    Path file = write(config(option("") + "," + option(""), CONSENT));
    assertThatThrownBy(() -> ConferenceConfigFile.load(file))
        .hasMessageContaining("duplicate option id ws-a");
  }

  @Test
  void invalidConsentIsRejected() throws IOException {
    Path file = write(config("", "{\"id\":\"data\",\"text\":\"t\",\"mandatory\":\"no\"}"));
    assertThatThrownBy(() -> ConferenceConfigFile.load(file))
        .hasMessageContaining("consents[0].mandatory");
  }

  @Test
  void missingFileStopsStartup() {
    assertThatThrownBy(() -> ConferenceConfigFile.load(dir.resolve("missing.json")))
        .hasMessage("CONFERENCE_CONFIG_FILE is invalid: cannot be read as JSON");
  }
}
