package si.konferenca.registration.infrastructure.jsoncopy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import si.konferenca.registration.application.Fixtures;
import si.konferenca.registration.domain.Registration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class FileJsonCopyStoreTest {

  @TempDir Path dir;

  @Test
  void fileNameUsesUtcTimestampAndId() {
    assertThat(new FileJsonCopyStore(dir).fileNameFor(Fixtures.registration()))
        .isEqualTo("20261006T180000Z_0b9f7a52-5c1e-4c55-9d1e-3f1f1f6a2b10.json");
  }

  @Test
  void serializesTheContractShapeWithoutAbsentTypeFields() {
    JsonNode json =
        JsonMapper.builder()
            .build()
            .readTree(new FileJsonCopyStore(dir).serialize(Fixtures.registration()));

    assertThat(json.path("schemaVersion").asInt()).isEqualTo(1);
    assertThat(json.path("receivedAt").asString()).isEqualTo("2026-10-06T18:00:00.123Z");
    assertThat(json.path("participant").has("studentId")).isFalse();
    assertThat(json.path("participant").path("organization").asString()).isEqualTo("IJS");
    assertThat(json.path("options").get(1).path("category").asString()).isEqualTo("EVENT");
    assertThat(json.path("consent").path("givenAt").asString())
        .isEqualTo("2026-10-06T18:00:00.123Z");
    assertThat(json.propertyNames())
        .containsExactly(
            "schemaVersion",
            "registrationId",
            "receivedAt",
            "type",
            "participant",
            "options",
            "consent");
  }

  @Test
  void writeLeavesOnlyTheFinalFileAndDeleteRemovesIt() throws Exception {
    FileJsonCopyStore store = new FileJsonCopyStore(dir);
    Registration r = Fixtures.registration();
    byte[] bytes = store.serialize(r);

    store.write(r, bytes);

    try (var files = Files.list(dir)) {
      assertThat(files.map(p -> p.getFileName().toString()).toList())
          .containsExactly(store.fileNameFor(r));
    }
    assertThat(Files.readAllBytes(dir.resolve(store.fileNameFor(r)))).isEqualTo(bytes);
    store.delete(r);
    try (var files = Files.list(dir)) {
      assertThat(files.toList()).isEmpty();
    }
  }

  @Test
  void writingTheSameRegistrationTwiceFails() {
    FileJsonCopyStore store = new FileJsonCopyStore(dir);
    Registration r = Fixtures.registration();
    store.write(r, new byte[] {1});

    assertThatThrownBy(() -> store.write(r, new byte[] {2}))
        .isInstanceOf(UncheckedIOException.class);
  }

  @Test
  void missingDirectoryFailsWithoutLeavingTemporaryFiles() {
    FileJsonCopyStore store = new FileJsonCopyStore(dir.resolve("missing"));

    assertThatThrownBy(() -> store.write(Fixtures.registration(), new byte[] {1}))
        .isInstanceOf(UncheckedIOException.class);
    assertThat(dir.resolve("missing")).doesNotExist();
  }

  @Test
  void deleteOfMissingFileIsQuiet() {
    new FileJsonCopyStore(dir).delete(Fixtures.registration());

    assertThat(List.of(dir.toFile().list())).isEmpty();
  }
}
