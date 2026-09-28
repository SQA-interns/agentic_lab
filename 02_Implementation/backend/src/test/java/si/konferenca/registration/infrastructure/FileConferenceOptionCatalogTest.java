package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionCategory;

class FileConferenceOptionCatalogTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @TempDir Path tempDir;

  private static AppProperties properties(String file) {
    return new AppProperties(
        new AppProperties.Recaptcha(true, null, null, "http://localhost"),
        new AppProperties.Mail("from@test", List.of()),
        new AppProperties.Backup("./build"),
        new AppProperties.Options(file),
        new AppProperties.Organizer("organizer", null),
        new AppProperties.RateLimit(
            new AppProperties.Limit(10, 600), new AppProperties.Limit(30, 600)),
        new AppProperties.Request(16384));
  }

  private FileConferenceOptionCatalog catalogFrom(String json) throws Exception {
    Path file = tempDir.resolve("options.json");
    Files.writeString(file, json, StandardCharsets.UTF_8);
    return new FileConferenceOptionCatalog(properties(file.toString()), mapper);
  }

  @Test
  void loadsBundledDefaultWhenNoFileConfigured() {
    FileConferenceOptionCatalog catalog = new FileConferenceOptionCatalog(properties(""), mapper);

    assertThat(catalog.findAll()).isNotEmpty();
    assertThat(catalog.findAll())
        .extracting(ConferenceOption::category)
        .contains(
            OptionCategory.WORKSHOP,
            OptionCategory.EVENT,
            OptionCategory.MEAL,
            OptionCategory.OTHER);
    assertThat(catalog.findById("ws-legacy")).map(ConferenceOption::active).contains(false);
  }

  @Test
  void loadsOptionsFromConfiguredFileInOrder() throws Exception {
    FileConferenceOptionCatalog catalog =
        catalogFrom(
            """
            {"options":[
              {"id":"meal-1","name":" Kosilo – dan 1 ","category":"MEAL","active":true},
              {"id":"ws-1","name":"Delavnica","category":"workshop","active":false}
            ]}
            """);

    assertThat(catalog.findAll())
        .containsExactly(
            new ConferenceOption("meal-1", "Kosilo – dan 1", OptionCategory.MEAL, true),
            new ConferenceOption("ws-1", "Delavnica", OptionCategory.WORKSHOP, false));
    assertThat(catalog.findById("ws-1")).isPresent();
    assertThat(catalog.findById("unknown")).isEmpty();
    assertThat(catalog.findById(null)).isEmpty();
  }

  @Test
  void skipsEntriesMissingRequiredAttributes() throws Exception { // AC-003-04
    FileConferenceOptionCatalog catalog =
        catalogFrom(
            """
            {"options":[
              {"name":"No id","category":"EVENT","active":true},
              {"id":"no-name","category":"EVENT","active":true},
              {"id":"no-status","name":"No status","category":"EVENT"},
              {"id":"no-category","name":"No category","active":true},
              {"id":"bad-category","name":"Bad","category":"PARTY","active":true},
              {"id":"Bad Id!","name":"Bad id","category":"EVENT","active":true},
              {"id":"status-text","name":"Text status","category":"EVENT","active":"yes"},
              {"id":"blank-name","name":"  ","category":"EVENT","active":true},
              "not an object",
              {"id":"ok","name":"Valid","category":"OTHER","active":true}
            ]}
            """);

    assertThat(catalog.findAll()).extracting(ConferenceOption::id).containsExactly("ok");
  }

  @Test
  void failsOnDuplicateIds() {
    assertThatThrownBy(
            () ->
                catalogFrom(
                    """
                    {"options":[
                      {"id":"dup","name":"A","category":"EVENT","active":true},
                      {"id":"dup","name":"B","category":"EVENT","active":true}
                    ]}
                    """))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("dup");
  }

  @Test
  void failsWithoutOptionsArray() {
    assertThatThrownBy(() -> catalogFrom("{\"workshops\":[]}"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void failsWhenConfiguredFileIsMissing() {
    String missing = tempDir.resolve("missing.json").toString();
    assertThatThrownBy(() -> new FileConferenceOptionCatalog(properties(missing), mapper))
        .isInstanceOf(UncheckedIOException.class);
  }
}
