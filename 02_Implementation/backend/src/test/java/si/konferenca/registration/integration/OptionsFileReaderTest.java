package si.konferenca.registration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.OptionDefinition;

class OptionsFileReaderTest {

  @TempDir Path dir;

  private OptionsFileReader readerFor(String json) throws IOException {
    Path file = dir.resolve("options.json");
    Files.writeString(file, json, StandardCharsets.UTF_8);
    return new OptionsFileReader(file);
  }

  @Test
  void readsEntriesInOrderAndTrimsNames() throws IOException {
    List<OptionDefinition> defs =
        readerFor(
                """
                {"options":[
                  {"id":"ws-1","category":"WORKSHOP","name":"  Delavnica č ","active":true},
                  {"id":"meal_2","category":"MEAL","name":"Kosilo","active":false}
                ]}""")
            .read();

    assertThat(defs)
        .containsExactly(
            new OptionDefinition("ws-1", OptionCategory.WORKSHOP, "Delavnica č", true),
            new OptionDefinition("meal_2", OptionCategory.MEAL, "Kosilo", false));
  }

  @Test
  void emptyListIsValid() throws IOException {
    assertThat(readerFor("{\"options\":[]}").read()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not json",
        "[]",
        "{}",
        "{\"options\":{}}",
        "{\"options\":[],\"extra\":1}",
        "{\"options\":[1]}",
        "{\"options\":[{\"id\":\"UPPER\",\"category\":\"MEAL\",\"name\":\"x\",\"active\":true}]}",
        "{\"options\":[{\"id\":\"-x\",\"category\":\"MEAL\",\"name\":\"x\",\"active\":true}]}",
        "{\"options\":[{\"id\":\"x\",\"category\":\"DRINK\",\"name\":\"x\",\"active\":true}]}",
        "{\"options\":[{\"id\":\"x\",\"category\":\"MEAL\",\"name\":\"  \",\"active\":true}]}",
        "{\"options\":[{\"id\":\"x\",\"category\":\"MEAL\",\"name\":\"x\",\"active\":\"yes\"}]}",
        "{\"options\":[{\"id\":\"x\",\"category\":\"MEAL\",\"name\":\"x\"}]}",
        "{\"options\":[{\"id\":\"x\",\"category\":\"MEAL\",\"name\":\"x\",\"active\":true,\"p\":1}]}",
        "{\"options\":[{\"id\":\"x\",\"category\":\"MEAL\",\"name\":\"a\",\"active\":true},"
            + "{\"id\":\"x\",\"category\":\"MEAL\",\"name\":\"b\",\"active\":true}]}"
      })
  void invalidFilesAreRejected(String json) throws IOException {
    OptionsFileReader reader = readerFor(json);

    assertThatThrownBy(reader::read)
        .isInstanceOf(OptionsFileReader.InvalidOptionsFileException.class);
  }

  @Test
  void nameLongerThan200CodePointsIsRejected() throws IOException {
    String name = "č".repeat(201);
    OptionsFileReader reader =
        readerFor(
            "{\"options\":[{\"id\":\"x\",\"category\":\"MEAL\",\"name\":\""
                + name
                + "\",\"active\":true}]}");

    assertThatThrownBy(reader::read)
        .isInstanceOf(OptionsFileReader.InvalidOptionsFileException.class);
  }

  @Test
  void missingFileIsAnIoError() {
    OptionsFileReader reader = new OptionsFileReader(dir.resolve("missing.json"));

    assertThatThrownBy(reader::read).isInstanceOf(IOException.class);
    assertThatThrownBy(reader::stamp).isInstanceOf(IOException.class);
  }

  @Test
  void stampChangesWithContentSize() throws IOException {
    OptionsFileReader reader = readerFor("{\"options\":[]}");
    OptionsFileReader.FileStamp before = reader.stamp();
    Files.writeString(reader.file(), "{\"options\":[] }");

    assertThat(reader.stamp()).isNotEqualTo(before);
  }
}
