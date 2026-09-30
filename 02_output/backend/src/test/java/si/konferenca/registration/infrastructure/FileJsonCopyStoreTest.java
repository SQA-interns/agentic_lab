package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import si.konferenca.registration.application.StorageException;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.ParticipantDetails;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class FileJsonCopyStoreTest {

  @TempDir Path dir;

  static Registration student() {
    return Registration.accept(
        UUID.fromString("0b8f7a4e-2f53-4f0a-9d1e-6f1c2b3a4d5e"),
        new ParticipantDetails(
            RegistrationType.STUDENT, "Žiga", "Čeč", "z@example.si", null, "UL", "RI", "6320"),
        List.of(
            new ConferenceOption(
                "meal", "Kosilo", Category.MEAL, true, EnumSet.allOf(RegistrationType.class))),
        List.of(new ConsentDefinition("dp", "Soglašam.", true)),
        Instant.parse("2026-09-30T10:00:00Z"));
  }

  @Test
  void writesTheCopyInSchemaShapeAndReadsItBack() throws IOException {
    FileJsonCopyStore store = new FileJsonCopyStore(dir.resolve("copies"));
    Registration r = student();

    store.write(r);

    Path file = dir.resolve("copies").resolve(r.reference() + ".json");
    assertThat(file).isRegularFile();
    byte[] bytes = store.read(r.reference());
    assertThat(bytes).isEqualTo(Files.readAllBytes(file));
    JsonNode json = JsonMapper.builder().build().readTree(bytes);
    assertThat(json.propertyNames())
        .containsExactly(
            "schemaVersion",
            "reference",
            "type",
            "submittedAt",
            "participant",
            "options",
            "consents");
    assertThat(json.path("participant").path("firstName").asString()).isEqualTo("Žiga");
    assertThat(json.path("participant").has("organization")).isFalse();
    assertThat(json.path("options").get(0).path("category").asString()).isEqualTo("MEAL");
    assertThat(json.path("consents").get(0).path("givenAt").asString())
        .isEqualTo("2026-09-30T10:00:00Z");
    assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("Čeč");
    try (Stream<Path> files = Files.list(dir.resolve("copies"))) {
      assertThat(files).hasSize(1);
    }
  }

  @Test
  void deleteRemovesTheCopyAndToleratesMissingFiles() {
    FileJsonCopyStore store = new FileJsonCopyStore(dir);
    Registration r = student();
    store.write(r);

    store.delete(r.reference());
    store.delete(r.reference());

    assertThat(dir.resolve(r.reference() + ".json")).doesNotExist();
    assertThatThrownBy(() -> store.read(r.reference())).isInstanceOf(StorageException.class);
  }

  @Test
  void failsWithoutLeavingFilesWhenTheDirectoryIsUnusable() throws IOException {
    Path notADirectory = Files.createFile(dir.resolve("file"));
    FileJsonCopyStore store = new FileJsonCopyStore(notADirectory.resolve("copies"));

    assertThatThrownBy(() -> store.write(student())).isInstanceOf(StorageException.class);
    assertThat(store.writable()).isFalse();
    assertThat(new FileJsonCopyStore(dir.resolve("new")).writable()).isTrue();
  }
}
