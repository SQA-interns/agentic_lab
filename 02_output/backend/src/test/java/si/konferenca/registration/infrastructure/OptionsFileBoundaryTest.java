package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.OptionCatalogue;

/** Limits of the options file at their exact bounds (D-12, D-16). */
class OptionsFileBoundaryTest {

  private final OptionsFileLoader loader = new OptionsFileLoader();

  private static ByteArrayResource json(String content) {
    return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8), "bounds");
  }

  private static String file(int maxSelections, int nameLength, int consentLength) {
    return "{\"categories\": {\"meal\": {\"maxSelections\": "
        + maxSelections
        + "}}, \"options\": [{\"id\": \"m\", \"name\": \""
        + "n".repeat(nameLength)
        + "\", \"category\": \"meal\", \"active\": true}], \"consents\": [{\"id\": \"c\","
        + " \"text\": \""
        + "t".repeat(consentLength)
        + "\", \"mandatory\": false}]}";
  }

  @Test
  void limitsAreInclusive() {
    OptionCatalogue lowest = loader.load(json(file(0, 1, 1)));
    OptionCatalogue highest = loader.load(json(file(50, 200, 2000)));

    assertThat(lowest.maxSelections(Category.MEAL)).isZero();
    assertThat(highest.maxSelections(Category.MEAL)).isEqualTo(50);
    assertThat(highest.option("m").orElseThrow().name()).hasSize(200);
    assertThat(highest.consents())
        .singleElement()
        .satisfies(c -> assertThat(c.text()).hasSize(2000));
    assertThat(highest.consent("c")).isPresent();
  }

  @Test
  void valuesJustOutsideTheLimitsAreRefused() {
    assertThatThrownBy(() -> loader.load(json(file(51, 1, 1))))
        .hasMessageContaining("maxSelections");
    assertThatThrownBy(() -> loader.load(json(file(-1, 1, 1))))
        .hasMessageContaining("maxSelections");
    assertThatThrownBy(() -> loader.load(json(file(1, 201, 1)))).hasMessageContaining("name");
    assertThatThrownBy(() -> loader.load(json(file(1, 1, 2001)))).hasMessageContaining("text");
  }
}
