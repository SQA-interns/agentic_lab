package si.konferenca.registration.adapter.out.jsoncopy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.Registration.Consent;
import si.konferenca.registration.domain.Registration.SelectedOption;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.TextField;

/** The JSON copy files (registration-copy.schema.json, BR-07). */
class FileJsonCopyStoreTest {

  private static final UUID ID = UUID.fromString("3f0e7a52-8a54-4d0e-9f43-1c6a2b7d9e10");
  private static final Instant ACCEPTED = Instant.parse("2026-10-02T10:15:30.123Z");

  @TempDir Path directory;

  private static Registration registration() {
    return new Registration(
        ID,
        RegistrationType.EXTERNAL,
        ACCEPTED,
        Map.of(
            TextField.FIRST_NAME, "Živa",
            TextField.LAST_NAME, "Čučnik \"Šu\" \\ </script>",
            TextField.EMAIL, "ziva@example.org",
            TextField.ORGANIZATION, "Inštitut"),
        List.of(new SelectedOption("ws-testing", "Delavnica", OptionCategory.WORKSHOP)),
        new Consent("personal-data", "Soglašam.", ACCEPTED));
  }

  private List<Path> files() throws IOException {
    try (Stream<Path> list = Files.list(directory)) {
      return list.toList();
    }
  }

  @Test
  void br07_copyIsWrittenAsUtf8JsonWithLineFeedsAndReadBackByteForByte() throws IOException {
    FileJsonCopyStore store = FileJsonCopyStore.open(directory);

    store.write(registration());

    Path file = directory.resolve(ID + ".json");
    assertThat(files()).containsExactly(file);
    String text = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(text)
        .isEqualTo(
            """
            {
              "schemaVersion" : 1,
              "id" : "3f0e7a52-8a54-4d0e-9f43-1c6a2b7d9e10",
              "type" : "EXTERNAL",
              "acceptedAt" : "2026-10-02T10:15:30.123Z",
              "firstName" : "Živa",
              "lastName" : "Čučnik \\"Šu\\" \\\\ </script>",
              "email" : "ziva@example.org",
              "organization" : "Inštitut",
              "options" : [
                {
                  "id" : "ws-testing",
                  "name" : "Delavnica",
                  "category" : "workshop"
                }
              ],
              "consent" : {
                "id" : "personal-data",
                "text" : "Soglašam.",
                "givenAt" : "2026-10-02T10:15:30.123Z"
              }
            }""");
    assertThat(text).doesNotContain("\r");
    assertThat(store.read(ID)).isEqualTo(Files.readAllBytes(file));
  }

  @Test
  void openCreatesAMissingDirectory() {
    Path nested = directory.resolve("a").resolve("b");

    FileJsonCopyStore.open(nested).write(registration());

    assertThat(nested.resolve(ID + ".json")).isRegularFile();
  }

  @Test
  void aFailedWriteLeavesNoPartialFileBehind() throws IOException {
    FileJsonCopyStore store = FileJsonCopyStore.open(directory);
    // A directory with the name of the target makes the final move fail.
    Files.createDirectory(directory.resolve(ID + ".json"));
    Files.writeString(directory.resolve(ID + ".json").resolve("blocker"), "x");

    assertThatThrownBy(() -> store.write(registration())).isInstanceOf(UncheckedIOException.class);

    assertThat(directory.resolve(ID + ".json.tmp")).doesNotExist();
  }

  @Test
  void deleteRemovesTheCopyAndNeverFails() throws IOException {
    FileJsonCopyStore store = FileJsonCopyStore.open(directory);
    store.write(registration());

    store.delete(ID);
    store.delete(ID);
    store.delete(UUID.randomUUID());

    assertThat(files()).isEmpty();
    assertThatThrownBy(() -> store.read(ID)).isInstanceOf(UncheckedIOException.class);
  }
}
