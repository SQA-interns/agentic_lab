package si.konferenca.registration.infrastructure.copy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class FileJsonCopyStoreTest {

  private static final UUID ID = UUID.fromString("3f1c2a9e-5b7d-4c1e-9a2b-8d6e4f0a1b2c");
  private static final Instant AT = Instant.parse("2026-10-06T08:00:00.123Z");

  @TempDir Path dir;

  private static Registration external() {
    return new Registration(
        ID,
        RegistrationType.EXTERNAL,
        new Participant("Žiga", "Čebašek", "z@example.si", "Šola", null, null, null),
        List.of(new Registration.SelectedOption("ev", "Večerja", OptionCategory.EVENT)),
        List.of(new Registration.GivenConsent("data", "I agree", AT)),
        AT);
  }

  private List<Path> files() throws IOException {
    try (Stream<Path> files = Files.list(dir)) {
      return files.toList();
    }
  }

  @Test
  void writesTheCopyAsUtf8JsonAndLeavesNoTemporaryFile() throws IOException {
    FileJsonCopyStore store = new FileJsonCopyStore(dir);

    byte[] written = store.write(external());

    Path file = dir.resolve(ID + ".json");
    assertThat(files()).containsExactly(file);
    assertThat(Files.readAllBytes(file)).isEqualTo(written);
    JsonNode json = JsonMapper.builder().build().readTree(written);
    assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
    assertThat(json.get("registrationId").asString()).isEqualTo(ID.toString());
    assertThat(json.get("type").asString()).isEqualTo("external");
    assertThat(json.get("receivedAt").asString()).isEqualTo("2026-10-06T08:00:00.123Z");
    JsonNode participant = json.get("participant");
    assertThat(participant.get("firstName").asString()).isEqualTo("Žiga");
    assertThat(participant.get("organization").asString()).isEqualTo("Šola");
    assertThat(participant.has("studyInstitution")).isFalse();
    assertThat(participant.has("studentId")).isFalse();
    assertThat(json.get("options").get(0).get("category").asString()).isEqualTo("event");
    assertThat(json.get("options").get(0).get("name").asString()).isEqualTo("Večerja");
    assertThat(json.get("consents").get(0).get("givenAt").asString())
        .isEqualTo("2026-10-06T08:00:00.123Z");
    assertThat(json.propertyNames())
        .containsExactly(
            "schemaVersion",
            "registrationId",
            "type",
            "receivedAt",
            "participant",
            "options",
            "consents");
  }

  @Test
  void studentCopyHasStudyFieldsAndNoOrganization() {
    Registration student =
        new Registration(
            ID,
            RegistrationType.STUDENT,
            new Participant("L", "K", "l@k.si", null, "UM", "P", "1"),
            List.of(),
            List.of(),
            AT);

    var json = FileJsonCopyStore.toJson(student);

    @SuppressWarnings("unchecked")
    var participant = (java.util.Map<String, Object>) json.get("participant");
    assertThat(participant).containsKeys("studyInstitution", "studyProgramme", "studentId");
    assertThat(participant).doesNotContainKey("organization");
  }

  @Test
  void deleteRemovesTheCopyAndToleratesAMissingOne() throws IOException {
    FileJsonCopyStore store = new FileJsonCopyStore(dir);
    store.write(external());

    store.delete(ID);
    store.delete(ID);

    assertThat(files()).isEmpty();
  }

  @Test
  void writeFailsWhenTheDirectoryIsNotADirectory() throws IOException {
    Path notADirectory = dir.resolve("file");
    Files.writeString(notADirectory, "x");
    FileJsonCopyStore store = new FileJsonCopyStore(notADirectory);

    assertThatThrownBy(() -> store.write(external())).isInstanceOf(UncheckedIOException.class);
  }

  @Test
  void prepareCreatesTheDirectoryOrStopsStartup() throws IOException {
    Path nested = dir.resolve("a").resolve("b");
    new FileJsonCopyStore(nested).prepare();
    assertThat(nested).isDirectory();

    Path file = dir.resolve("blocked");
    Files.writeString(file, "x");
    assertThatThrownBy(() -> new FileJsonCopyStore(file.resolve("sub")).prepare())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("JSON_COPY_DIR cannot be created");
  }
}
