package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.DefaultResourceLoader;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionCategory;

class OptionsFileCatalogTest {

  private static final String CONSENTS =
      "\"consents\":[{\"id\":\"privacy\",\"text\":\"P\",\"required\":true}]";

  @TempDir Path dir;

  private OptionsFileCatalog load(String json) throws IOException {
    Path file = Files.writeString(dir.resolve("options.json"), json);
    return new OptionsFileCatalog(
        TestProperties.with(file.toUri().toString(), dir.toString()), new DefaultResourceLoader());
  }

  @Test
  void loadsOptionsInOrderAndSeparatesInactive() throws IOException {
    OptionsFileCatalog catalog =
        load(
            "{\"options\":["
                + "{\"id\":\"b\",\"name\":\"B\",\"category\":\"meal\",\"active\":true},"
                + "{\"id\":\"a\",\"name\":\"A\",\"category\":\"workshop\",\"active\":false},"
                + "{\"id\":\"c\",\"name\":\"C\",\"category\":\"other\",\"active\":true}],"
                + CONSENTS
                + "}");

    assertThat(catalog.activeOptions()).extracting(ConferenceOption::id).containsExactly("b", "c");
    assertThat(catalog.find("a")).get().extracting(ConferenceOption::active).isEqualTo(false);
    assertThat(catalog.find("b"))
        .get()
        .extracting(ConferenceOption::category)
        .isEqualTo(OptionCategory.MEAL);
    assertThat(catalog.find("x")).isEmpty();
    assertThat(catalog.consents()).hasSize(1);
    assertThat(catalog.consents().get(0).required()).isTrue();
  }

  @Test
  void loadsTheShippedClasspathFile() {
    OptionsFileCatalog catalog =
        new OptionsFileCatalog(
            TestProperties.with("classpath:conference-options.json", "x"),
            new DefaultResourceLoader());

    assertThat(catalog.activeOptions()).isNotEmpty();
    assertThat(catalog.consents()).extracting(c -> c.id()).contains("privacy");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not json",
        "[]",
        "{\"options\":[]}",
        "{\"options\":{}," + CONSENTS + "}",
        "{\"options\":[]," + CONSENTS + ",\"extra\":1}",
        "{\"options\":[1]," + CONSENTS + "}",
        "{\"options\":[{\"id\":\"A\",\"name\":\"n\",\"category\":\"meal\",\"active\":true}],"
            + CONSENTS
            + "}",
        "{\"options\":[{\"id\":\"a\",\"name\":\" \",\"category\":\"meal\",\"active\":true}],"
            + CONSENTS
            + "}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"n\",\"category\":\"party\",\"active\":true}],"
            + CONSENTS
            + "}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"n\",\"category\":\"meal\",\"active\":\"yes\"}],"
            + CONSENTS
            + "}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"n\",\"category\":\"meal\"}]," + CONSENTS + "}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"n\",\"category\":\"meal\",\"active\":true,"
            + "\"price\":1}],"
            + CONSENTS
            + "}",
        "{\"options\":[{\"id\":\"a\",\"name\":\"n\",\"category\":\"meal\",\"active\":true},"
            + "{\"id\":\"a\",\"name\":\"m\",\"category\":\"meal\",\"active\":true}],"
            + CONSENTS
            + "}",
        "{\"options\":[],\"consents\":[{\"id\":\"p\",\"text\":\"t\",\"required\":1}]}",
        "{\"options\":[],\"consents\":[{\"id\":\"p\",\"text\":\"\",\"required\":true}]}",
        "{\"options\":[],\"consents\":[{\"id\":\"p\",\"text\":\"t\",\"required\":true},"
            + "{\"id\":\"p\",\"text\":\"u\",\"required\":false}]}",
        "{\"options\":[],\"consents\":{}}"
      })
  void refusesInvalidFiles(String json) {
    assertThatThrownBy(() -> load(json))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Invalid conference options configuration");
  }

  @Test
  void refusesTooLongName() {
    String name = "n".repeat(201);

    assertThatThrownBy(
            () ->
                load(
                    "{\"options\":[{\"id\":\"a\",\"name\":\""
                        + name
                        + "\",\"category\":\"meal\",\"active\":true}],"
                        + CONSENTS
                        + "}"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void acceptsNameOfExactlyTheMaximumLength() throws IOException {
    String name = "n".repeat(200);

    OptionsFileCatalog catalog =
        load(
            "{\"options\":[{\"id\":\"a\",\"name\":\""
                + name
                + "\",\"category\":\"meal\",\"active\":true}],"
                + CONSENTS
                + "}");

    assertThat(catalog.find("a")).get().extracting(ConferenceOption::name).isEqualTo(name);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"options\":[],\"consents\":[{\"id\":\"p\",\"text\":\"t\"}]}",
        "{\"options\":[],\"consents\":[{\"id\":\"p\",\"text\":\"t\",\"required\":true,\"x\":1}]}",
        "{\"options\":[],\"consents\":[\"privacy\"]}"
      })
  void refusesConsentsWithWrongFields(String json) {
    assertThatThrownBy(() -> load(json))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("consent must");
  }

  @Test
  void refusesMissingFile() {
    assertThatThrownBy(
            () ->
                new OptionsFileCatalog(
                    TestProperties.with(dir.resolve("none.json").toUri().toString(), "x"),
                    new DefaultResourceLoader()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not found");
  }
}
