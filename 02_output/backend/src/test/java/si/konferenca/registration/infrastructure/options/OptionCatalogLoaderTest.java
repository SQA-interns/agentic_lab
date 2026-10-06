package si.konferenca.registration.infrastructure.options;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import si.konferenca.registration.domain.OptionCatalog;
import si.konferenca.registration.domain.RegistrationType;

class OptionCatalogLoaderTest {

  @TempDir Path dir;

  private OptionCatalog load(String yaml) throws Exception {
    Path f = dir.resolve("o.yaml");
    Files.writeString(f, yaml);
    return OptionCatalogLoader.load(new DefaultResourceLoader(), "file:" + f);
  }

  private static final String CONSENT = "conference:\n  consent: { id: data, text: \"I agree\" }\n";

  @Test
  void bundledDefaultConfigurationLoads() {
    OptionCatalog c =
        OptionCatalogLoader.load(new DefaultResourceLoader(), "classpath:conference-options.yaml");

    assertThat(c.activeOptions()).isNotEmpty();
    assertThat(c.consent().id()).isEqualTo("data-processing");
  }

  @Test
  void offeredToDefaultsToBothTypesAndNamesAreTrimmed() throws Exception {
    OptionCatalog c =
        load(
            CONSENT + "  options:\n    - { id: a, name: \" A \", category: MEAL, active: true }\n");

    assertThat(c.find("a").orElseThrow().offeredTo())
        .containsExactlyInAnyOrder(RegistrationType.EXTERNAL, RegistrationType.STUDENT);
    assertThat(c.find("a").orElseThrow().name()).isEqualTo("A");
  }

  @Test
  void noOptionsIsAllowed() throws Exception {
    assertThat(load(CONSENT).activeOptions()).isEmpty();
  }

  @Test
  void missingFileIsReported() {
    assertThatThrownBy(
            () -> OptionCatalogLoader.load(new DefaultResourceLoader(), "file:/nonexistent/o.yaml"))
        .hasMessageContaining("not found");
  }

  @Test
  void missingSectionIsReported() {
    assertThatThrownBy(() -> load("other: 1\n")).hasMessageContaining("No 'conference' section");
  }

  @Test
  void missingConsentIsReported() {
    assertThatThrownBy(() -> load("conference:\n  options: []\n")).hasMessageContaining("consent");
  }

  @Test
  void invalidOptionIdIsReported() {
    assertThatThrownBy(
            () ->
                load(
                    CONSENT
                        + "  options:\n    - { id: Bad_Id, name: x, category: MEAL, active: true }\n"))
        .hasMessageContaining("Bad_Id");
  }

  @Test
  void missingCategoryAndActiveAreReported() {
    assertThatThrownBy(() -> load(CONSENT + "  options:\n    - { id: a, name: x, active: true }\n"))
        .hasMessageContaining("no category");
    assertThatThrownBy(
            () -> load(CONSENT + "  options:\n    - { id: a, name: x, category: MEAL }\n"))
        .hasMessageContaining("active");
  }
}
