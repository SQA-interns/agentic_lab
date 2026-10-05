package si.konferenca.registration.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.DefaultResourceLoader;
import si.konferenca.registration.domain.OptionCategory;
import tools.jackson.databind.json.JsonMapper;

class JsonOptionsFileTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final DefaultResourceLoader LOADER = new DefaultResourceLoader();

  @TempDir Path dir;

  private JsonOptionsFile load(String json) throws Exception {
    Path file = dir.resolve("options.json");
    Files.writeString(file, json, UTF_8);
    return new JsonOptionsFile(LOADER, MAPPER, file.toString());
  }

  private static String options(String option) {
    return "{\"consent\":{\"id\":\"c-1\",\"text\":\"Soglasje čšž\"},\"options\":[" + option + "]}";
  }

  private static final String VALID =
      "{\"id\":\"ws-1\",\"name\":\"Delavnica\",\"category\":\"workshop\",\"active\":true}";

  @Test
  void readsTheBundledContractExample() {
    var catalog =
        new JsonOptionsFile(LOADER, MAPPER, "classpath:contracts/conference-options.example.json");

    assertThat(catalog.options().options()).hasSize(8);
    assertThat(catalog.options().active()).hasSize(7);
    assertThat(catalog.options().consent().id()).isEqualTo("personal-data-v1");
  }

  @Test
  void readsAFileFromThePath() throws Exception {
    var options = load(options(VALID)).options();

    assertThat(options.consent().text()).isEqualTo("Soglasje čšž");
    assertThat(options.find("ws-1").orElseThrow().category()).isEqualTo(OptionCategory.WORKSHOP);
    assertThat(options.find("nope")).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "{\"id\":\"ws-1\",\"name\":\"N\",\"category\":\"workshop\",\"active\":true,\"x\":1}|unknown property x",
        "{\"id\":\"WS\",\"name\":\"N\",\"category\":\"workshop\",\"active\":true}|option id must match",
        "{\"id\":\"ws-1\",\"name\":\"\",\"category\":\"workshop\",\"active\":true}|name must be a non-empty string",
        "{\"id\":\"ws-1\",\"name\":\"N\",\"category\":\"party\",\"active\":true}|unknown category",
        "{\"id\":\"ws-1\",\"name\":\"N\",\"category\":1,\"active\":true}|unknown category",
        "{\"id\":\"ws-1\",\"name\":\"N\",\"category\":\"meal\",\"active\":\"yes\"}|needs active true or false",
        "{\"id\":\"ws-1\",\"name\":\"N\",\"category\":\"meal\"}|misses active",
        "[]|option must be an object"
      })
  void rejectsInvalidOptions(String option, String message) {
    assertThatThrownBy(() -> load(options(option)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(message);
  }

  @Test
  void rejectsDuplicateIdsAndTooLongNames() {
    assertThatThrownBy(() -> load(options(VALID + "," + VALID)))
        .hasMessageContaining("duplicate option id ws-1");
    String longName = "x".repeat(201);
    assertThatThrownBy(
            () ->
                load(
                    options(
                        "{\"id\":\"a\",\"name\":\""
                            + longName
                            + "\",\"category\":\"meal\",\"active\":true}")))
        .hasMessageContaining("longer than 200");
  }

  @Test
  void rejectsBadTopLevelAndConsent() {
    assertThatThrownBy(() -> load("[]")).hasMessageContaining("options file must be an object");
    assertThatThrownBy(() -> load("{\"consent\":{\"id\":\"c\",\"text\":\"t\"}}"))
        .hasMessageContaining("misses options");
    assertThatThrownBy(() -> load("{\"consent\":{\"id\":\"c\"},\"options\":[]}"))
        .hasMessageContaining("consent misses text");
    assertThatThrownBy(() -> load("{\"consent\":{\"id\":\"c\",\"text\":\"t\"},\"options\":{}}"))
        .hasMessageContaining("options must be an array");
    assertThatThrownBy(() -> load("not json")).hasMessageContaining("cannot be read");
  }

  @Test
  void acceptsExactlyOneHundredOptionsAndNamesOfTwoHundredCharacters() throws Exception {
    StringBuilder hundred = new StringBuilder();
    for (int i = 0; i < 100; i++) {
      hundred
          .append(i == 0 ? "" : ",")
          .append("{\"id\":\"o")
          .append(i)
          .append("\",\"name\":\"")
          .append("č".repeat(200))
          .append("\",\"category\":\"other\",\"active\":true}");
    }
    assertThat(load(options(hundred.toString())).options().options()).hasSize(100);
  }

  @Test
  void rejectsMoreThanOneHundredOptions() {
    StringBuilder many = new StringBuilder();
    for (int i = 0; i < 101; i++) {
      many.append(i == 0 ? "" : ",")
          .append("{\"id\":\"o")
          .append(i)
          .append("\",\"name\":\"N\",\"category\":\"other\",\"active\":true}");
    }
    assertThatThrownBy(() -> load(options(many.toString()))).hasMessageContaining("at most 100");
  }

  @Test
  void rejectsMissingOrUnsetLocation() {
    assertThatThrownBy(() -> new JsonOptionsFile(LOADER, MAPPER, " "))
        .hasMessageContaining("CONFERENCE_OPTIONS_FILE is not set");
    assertThatThrownBy(
            () -> new JsonOptionsFile(LOADER, MAPPER, dir.resolve("none.json").toString()))
        .hasMessageContaining("cannot be read");
  }
}
