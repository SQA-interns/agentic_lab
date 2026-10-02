package si.konferenca.registration.adapter.out.options;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionCategory;

/** Loading of the options file (conference-options.schema.json, AR-04). */
class FileOptionsCatalogueTest {

  @TempDir Path directory;

  private Path file(String content) throws IOException {
    return Files.writeString(directory.resolve("options.json"), content);
  }

  @Test
  void activeOptionsAreKeptInFileOrderAndInactiveOnesAreNotSelectable() throws IOException {
    FileOptionsCatalogue catalogue =
        FileOptionsCatalogue.load(
            file(
                """
                {"options":[
                  {"id":"b","name":"Večerja","category":"meal","active":true},
                  {"id":"x","name":"Staro","category":"other","active":false},
                  {"id":"a","name":"Delavnica","category":"workshop","active":true}]}
                """));

    assertThat(catalogue.activeOptions())
        .containsExactly(
            new ConferenceOption("b", "Večerja", OptionCategory.MEAL, true),
            new ConferenceOption("a", "Delavnica", OptionCategory.WORKSHOP, true));
    assertThat(catalogue.findActive("a")).isPresent();
    assertThat(catalogue.findActive("x")).isEmpty();
    assertThat(catalogue.findActive("unknown")).isEmpty();
  }

  @Test
  void anEmptyOptionListIsValid() throws IOException {
    assertThat(FileOptionsCatalogue.load(file("{\"options\":[]}")).activeOptions()).isEmpty();
  }

  @Test
  void aMissingFileStopsTheStart() {
    assertThatThrownBy(() -> FileOptionsCatalogue.load(directory.resolve("missing.json")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cannot be read");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not json",
        "[]",
        "{}",
        "{\"options\":{}}",
        "{\"options\":[],\"extra\":1}",
        "{\"options\":[\"a\"]}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"A\",\"category\":\"workshop\"}]}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"A\",\"category\":\"workshop\",\"active\":true,\"x\":1}]}",
        "{\"options\":[{\"id\":\"A b\",\"name\":\"A\",\"category\":\"workshop\",\"active\":true}]}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"  \",\"category\":\"workshop\",\"active\":true}]}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"A\",\"category\":\"party\",\"active\":true}]}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"A\",\"category\":\"workshop\",\"active\":\"yes\"}]}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"A\",\"category\":\"meal\",\"active\":true},"
            + "{\"id\":\"a\",\"name\":\"B\",\"category\":\"meal\",\"active\":false}]}"
      })
  void aFileThatBreaksTheContractStopsTheStart(String content) throws IOException {
    Path file = file(content);

    assertThatThrownBy(() -> FileOptionsCatalogue.load(file))
        .isInstanceOf(IllegalStateException.class);
  }
}
