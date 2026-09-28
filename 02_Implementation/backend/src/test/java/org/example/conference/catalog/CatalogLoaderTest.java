package org.example.conference.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** AC-003-01, AC-003-04: catalog parsing and fail-fast validation. */
class CatalogLoaderTest {

  private static Catalog parse(String yaml) throws Exception {
    return CatalogLoader.parse(new ObjectMapper(new YAMLFactory()).readTree(yaml));
  }

  @Test
  void loadsShippedCatalogs() {
    for (String file :
        new String[] {
          "../config/catalog.yaml",
          "../config/fixtures/catalog-consent-fixture.yaml",
          "../config/fixtures/catalog-changed.yaml"
        }) {
      Catalog catalog = CatalogLoader.load(Path.of(file));
      assertThat(catalog.activeOptions(OptionGroup.WORKSHOPS)).isNotEmpty();
    }
    assertThat(CatalogLoader.load(Path.of("../config/catalog.yaml")).consents()).isEmpty();
  }

  @Test
  void onlyActiveKnownOptionsAreSelectable() throws Exception {
    Catalog catalog =
        parse(
            """
            conferenceName: C
            workshops:
              - {id: ws-a, name: A, active: true}
              - {id: ws-b, name: B, active: false}
            """);
    assertThat(catalog.findActive(OptionGroup.WORKSHOPS, "ws-a")).isPresent();
    assertThat(catalog.findActive(OptionGroup.WORKSHOPS, "ws-b")).isEmpty();
    assertThat(catalog.findActive(OptionGroup.MEALS, "ws-a")).isEmpty();
    assertThat(catalog.activeOptions(OptionGroup.WORKSHOPS)).hasSize(1);
    assertThat(catalog.options(OptionGroup.EVENTS)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "workshops: [{id: ws-a, name: A, active: true}]",
        "conferenceName: C\nworkshops: [{id: ws-a, name: A, active: true}, {id: ws-a, name: B, active: true}]",
        "conferenceName: C\nworkshops: [{id: 'WS A', name: A, active: true}]",
        "conferenceName: C\nworkshops: [{id: ws-a, name: '  ', active: true}]",
        "conferenceName: C\nworkshops: [{id: ws-a, name: A}]",
        "conferenceName: C\nworkshops: [{id: ws-a, name: A, active: 'yes'}]",
        "conferenceName: C\nparking: []",
        "conferenceName: C\nconsents: [{id: c1, text: T}]",
        "conferenceName: C\nworkshops: {id: ws-a}",
        "- just a list"
      })
  void invalidCatalogsFailFast(String yaml) {
    assertThatThrownBy(() -> parse(yaml)).isInstanceOf(InvalidCatalogException.class);
  }

  @Test
  void missingFileFailsFast() {
    assertThatThrownBy(() -> CatalogLoader.load(Path.of("does-not-exist.yaml")))
        .isInstanceOf(InvalidCatalogException.class);
  }
}
