package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.OptionCatalogue;
import si.konferenca.registration.domain.RegistrationType;

class OptionsFileLoaderTest {

  private static final String CONSENTS =
      "\"consents\": [{\"id\": \"data\", \"text\": \"I agree.\", \"mandatory\": true}]";

  private final OptionsFileLoader loader = new OptionsFileLoader();

  private static ByteArrayResource json(String content) {
    return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8), "test options");
  }

  @Test
  void bundledDefaultFileLoads() {
    OptionCatalogue catalogue = loader.load(new ClassPathResource("conference-options.json"));

    assertThat(catalogue.activeOptions(Category.WORKSHOP)).hasSize(2);
    assertThat(catalogue.maxSelections(Category.EVENT)).isEqualTo(2);
    assertThat(catalogue.option("ws-legacy"))
        .hasValueSatisfying(o -> assertThat(o.active()).isFalse());
    assertThat(catalogue.option("ev-industry-dinner").orElseThrow().availableTo())
        .containsExactly(RegistrationType.EXTERNAL);
  }

  @Test
  void omittedCategoryLimitDefaultsToOneAndAvailabilityToBothTypes() {
    OptionCatalogue catalogue =
        loader.load(
            json(
                "{\"categories\": {}, \"options\": [{\"id\": \"m\", \"name\": \"Meal\","
                    + " \"category\": \"meal\", \"active\": true}], "
                    + CONSENTS
                    + "}"));

    assertThat(catalogue.maxSelections(Category.MEAL)).isEqualTo(1);
    assertThat(catalogue.option("m").orElseThrow().availableTo())
        .containsExactlyInAnyOrder(RegistrationType.EXTERNAL, RegistrationType.STUDENT);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "malformed JSON|{|Unexpected",
        "missing consents|{\"categories\": {}, \"options\": []}|required",
        "empty consents|{\"categories\": {}, \"options\": [], \"consents\": []}|at least one consent",
        "unknown category key|{\"categories\": {\"talks\": {\"maxSelections\": 1}}, \"options\": [], "
            + CONSENTS
            + "}|unknown category talks",
        "limit out of range|{\"categories\": {\"meal\": {\"maxSelections\": 51}}, \"options\": [], "
            + CONSENTS
            + "}|maxSelections of meal",
        "negative limit|{\"categories\": {\"meal\": {\"maxSelections\": -1}}, \"options\": [], "
            + CONSENTS
            + "}|maxSelections of meal",
        "missing limit|{\"categories\": {\"meal\": {}}, \"options\": [], "
            + CONSENTS
            + "}|maxSelections of meal",
        "bad option id|{\"categories\": {}, \"options\": [{\"id\": \"Bad Id\", \"name\": \"x\","
            + " \"category\": \"meal\", \"active\": true}], "
            + CONSENTS
            + "}|option id Bad Id",
        "missing active|{\"categories\": {}, \"options\": [{\"id\": \"x\", \"name\": \"x\","
            + " \"category\": \"meal\"}], "
            + CONSENTS
            + "}|active is required",
        "missing category|{\"categories\": {}, \"options\": [{\"id\": \"x\", \"name\": \"x\","
            + " \"active\": true}], "
            + CONSENTS
            + "}|unknown category",
        "blank name|{\"categories\": {}, \"options\": [{\"id\": \"x\", \"name\": \" \","
            + " \"category\": \"meal\", \"active\": true}], "
            + CONSENTS
            + "}|name is required",
        "empty availableTo|{\"categories\": {}, \"options\": [{\"id\": \"x\", \"name\": \"x\","
            + " \"category\": \"meal\", \"active\": true, \"availableTo\": []}], "
            + CONSENTS
            + "}|availableTo",
        "unknown type|{\"categories\": {}, \"options\": [{\"id\": \"x\", \"name\": \"x\","
            + " \"category\": \"meal\", \"active\": true, \"availableTo\": [\"GUEST\"]}], "
            + CONSENTS
            + "}|GUEST",
        "duplicate consent|{\"categories\": {}, \"options\": [], \"consents\": [{\"id\": \"a\","
            + " \"text\": \"t\", \"mandatory\": true}, {\"id\": \"a\", \"text\": \"t\","
            + " \"mandatory\": false}]}|duplicate consent id a",
        "consent without text|{\"categories\": {}, \"options\": [], \"consents\": [{\"id\": \"a\","
            + " \"mandatory\": true}]}|consent a: text",
        "consent without mandatory|{\"categories\": {}, \"options\": [], \"consents\": [{\"id\":"
            + " \"a\", \"text\": \"t\"}]}|consent a: mandatory"
      })
  void invalidFilesAreRefusedNamingTheProblem(String description, String content, String message) {
    assertThatThrownBy(() -> loader.load(json(content)))
        .isInstanceOf(OptionsFileLoader.InvalidOptionsException.class)
        .hasMessageContaining("test options")
        .hasMessageContaining(message);
  }

  @Test
  void missingFileIsReported() {
    assertThatThrownBy(() -> loader.load(new ClassPathResource("no-such-options.json")))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("not readable");
  }
}
