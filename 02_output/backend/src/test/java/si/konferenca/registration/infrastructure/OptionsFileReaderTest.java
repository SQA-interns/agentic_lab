package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.config.StartupChecksTest;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.OptionCatalogue;
import si.konferenca.registration.domain.RegistrationType;
import tools.jackson.databind.json.JsonMapper;

class OptionsFileReaderTest {

  private static final String VALID =
      """
      {"categories": {"WORKSHOP": 2},
       "options": [
         {"id": "ws-a", "name": "A", "category": "WORKSHOP", "active": true},
         {"id": "ev-s", "name": "S", "category": "EVENT", "active": false, "registrationTypes": ["STUDENT"]}],
       "consents": [{"id": "data", "text": "I agree", "mandatory": true}]}
      """;

  private static OptionCatalogue parse(String json) {
    return OptionsFileReader.parse(JsonMapper.builder().build().readTree(json));
  }

  @Test
  @DisplayName("AR-04 a valid file becomes the catalogue; registrationTypes default to both")
  void parsesValidFile() {
    OptionCatalogue c = parse(VALID);
    assertThat(c.limit(Category.WORKSHOP)).isEqualTo(2);
    assertThat(c.limit(Category.MEAL)).isEqualTo(1);
    assertThat(c.option("ws-a").orElseThrow().registrationTypes())
        .containsExactlyInAnyOrder(RegistrationType.EXTERNAL, RegistrationType.STUDENT);
    assertThat(c.option("ev-s").orElseThrow().registrationTypes())
        .containsExactly(RegistrationType.STUDENT);
    assertThat(c.activeOptions()).hasSize(1);
  }

  @ParameterizedTest(name = "AR-04 invalid file rejected: {0}")
  @ValueSource(
      strings = {
        "[]",
        "{\"options\": [], \"consents\": [{\"id\": \"d\", \"text\": \"t\", \"mandatory\": true}]}",
        "{\"categories\": {\"SPA\": 1}, \"options\": [], \"consents\": [{\"id\": \"d\", \"text\": \"t\", \"mandatory\": true}]}",
        "{\"categories\": {\"MEAL\": 51}, \"options\": [], \"consents\": [{\"id\": \"d\", \"text\": \"t\", \"mandatory\": true}]}",
        "{\"categories\": {\"MEAL\": \"2\"}, \"options\": [], \"consents\": [{\"id\": \"d\", \"text\": \"t\", \"mandatory\": true}]}",
        "{\"categories\": {}, \"options\": [{\"id\": \"Bad Id\", \"name\": \"n\", \"category\": \"MEAL\", \"active\": true}], \"consents\": [{\"id\": \"d\", \"text\": \"t\", \"mandatory\": true}]}",
        "{\"categories\": {}, \"options\": [{\"id\": \"a\", \"name\": \"\", \"category\": \"MEAL\", \"active\": true}], \"consents\": [{\"id\": \"d\", \"text\": \"t\", \"mandatory\": true}]}",
        "{\"categories\": {}, \"options\": [{\"id\": \"a\", \"name\": \"n\", \"category\": \"SPA\", \"active\": true}], \"consents\": [{\"id\": \"d\", \"text\": \"t\", \"mandatory\": true}]}",
        "{\"categories\": {}, \"options\": [{\"id\": \"a\", \"name\": \"n\", \"category\": \"MEAL\", \"active\": \"yes\"}], \"consents\": [{\"id\": \"d\", \"text\": \"t\", \"mandatory\": true}]}",
        "{\"categories\": {}, \"options\": [{\"id\": \"a\", \"name\": \"n\", \"category\": \"MEAL\"}], \"consents\": [{\"id\": \"d\", \"text\": \"t\", \"mandatory\": true}]}",
        "{\"categories\": {}, \"options\": [{\"id\": \"a\", \"name\": \"n\", \"category\": \"MEAL\", \"active\": true, \"price\": 3}], \"consents\": [{\"id\": \"d\", \"text\": \"t\", \"mandatory\": true}]}",
        "{\"categories\": {}, \"options\": [{\"id\": \"a\", \"name\": \"n\", \"category\": \"MEAL\", \"active\": true, \"registrationTypes\": []}], \"consents\": [{\"id\": \"d\", \"text\": \"t\", \"mandatory\": true}]}",
        "{\"categories\": {}, \"options\": [{\"id\": \"a\", \"name\": \"n\", \"category\": \"MEAL\", \"active\": true, \"registrationTypes\": [\"STUDENT\", \"STUDENT\"]}], \"consents\": [{\"id\": \"d\", \"text\": \"t\", \"mandatory\": true}]}",
        "{\"categories\": {}, \"options\": [{\"id\": \"a\", \"name\": \"n\", \"category\": \"MEAL\", \"active\": true}, {\"id\": \"a\", \"name\": \"m\", \"category\": \"MEAL\", \"active\": true}], \"consents\": [{\"id\": \"d\", \"text\": \"t\", \"mandatory\": true}]}",
        "{\"categories\": {}, \"options\": [], \"consents\": []}",
        "{\"categories\": {}, \"options\": [], \"consents\": [{\"id\": \"d\", \"text\": \"t\", \"mandatory\": false}]}",
        "{\"categories\": {}, \"options\": {}, \"consents\": [{\"id\": \"d\", \"text\": \"t\", \"mandatory\": true}]}"
      })
  void rejectsInvalidFiles(String json) {
    assertThatThrownBy(() -> parse(json))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("invalid conference options file");
  }

  @Test
  void readsConfiguredFile(@TempDir Path dir) throws Exception {
    Path file = Files.writeString(dir.resolve("o.json"), VALID);
    OptionsFileReader reader = new OptionsFileReader(withOptionsFile("test", file.toString()));
    assertThat(reader.read().option("ws-a")).isPresent();
  }

  @Test
  void localFallsBackToBundledFile() {
    OptionsFileReader reader = new OptionsFileReader(withOptionsFile("local", ""));
    assertThat(reader.read().activeOptions()).isNotEmpty();
  }

  @Test
  void productionWithoutFileFails() {
    OptionsFileReader reader = new OptionsFileReader(withOptionsFile("production", ""));
    assertThatThrownBy(reader::read).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void missingOrMalformedFileFails(@TempDir Path dir) throws Exception {
    OptionsFileReader missing =
        new OptionsFileReader(withOptionsFile("test", dir.resolve("none.json").toString()));
    assertThatThrownBy(missing::read)
        .hasMessageStartingWith("conference options file cannot be read");
    Path broken = Files.writeString(dir.resolve("b.json"), "{ not json");
    OptionsFileReader malformed = new OptionsFileReader(withOptionsFile("test", broken.toString()));
    assertThatThrownBy(malformed::read)
        .hasMessageStartingWith("conference options file cannot be read");
  }

  static AppProperties withOptionsFile(String environment, String file) {
    AppProperties p = StartupChecksTest.validProduction();
    return new AppProperties(
        environment,
        p.conferenceName(),
        file,
        p.jsonCopyDir(),
        p.mailFrom(),
        p.recaptcha(),
        p.organizer(),
        p.corsAllowedOrigin(),
        p.rateLimit(),
        p.maxRequestBytes(),
        p.retention());
  }
}
