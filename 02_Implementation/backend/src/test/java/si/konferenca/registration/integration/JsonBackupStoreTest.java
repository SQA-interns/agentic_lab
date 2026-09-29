package si.konferenca.registration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.RegistrationSnapshot;
import si.konferenca.registration.domain.RegistrationSnapshot.SnapshotOption;
import si.konferenca.registration.domain.RegistrationType;

class JsonBackupStoreTest {

  @TempDir Path dir;

  static RegistrationSnapshot snapshot(UUID id) {
    Instant t = Instant.parse("2026-09-29T10:15:30.123456Z");
    return new RegistrationSnapshot(
        1,
        id,
        t,
        RegistrationType.STUDENT,
        "Živa",
        "Čepič",
        "ziva@x.si",
        null,
        "FRI",
        "RI",
        "6320",
        t,
        List.of(new SnapshotOption("ws-1", OptionCategory.WORKSHOP, "Delavnica")));
  }

  @Test
  void writesAtomicallyAndReadsBack() throws IOException {
    JsonBackupStore store = new JsonBackupStore(dir.resolve("backups"));
    store.initialize();
    UUID id = UUID.randomUUID();
    byte[] json = store.serialize(snapshot(id));

    store.write(id, json);

    assertThat(Files.readAllBytes(store.fileOf(id))).isEqualTo(json);
    try (Stream<Path> files = Files.list(dir.resolve("backups"))) {
      assertThat(files).hasSize(1);
    }
    List<JsonBackupStore.Entry> entries = store.readAll();
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).snapshot()).isEqualTo(snapshot(id));
    assertThat(new String(json, StandardCharsets.UTF_8))
        .contains("\"submittedAt\" : \"2026-09-29T10:15:30.123456Z\"")
        .contains("Živa")
        .contains("\"organization\" : null");
  }

  @Test
  void writeRefusesToOverwriteAnExistingTemporaryFile() throws IOException {
    JsonBackupStore store = new JsonBackupStore(dir);
    UUID id = UUID.randomUUID();
    Files.writeString(dir.resolve(id + ".json.tmp"), "stale");

    assertThatThrownBy(() -> store.write(id, new byte[] {1})).isInstanceOf(IOException.class);
  }

  @Test
  void writeFailsWhenTheDirectoryIsNotADirectory() throws IOException {
    Path notADir = dir.resolve("file");
    Files.writeString(notADir, "x");
    JsonBackupStore store = new JsonBackupStore(notADir);

    assertThatThrownBy(() -> store.write(UUID.randomUUID(), new byte[] {1}))
        .isInstanceOf(IOException.class);
  }

  @Test
  void invalidOrMismatchedFilesAreReportedAsUnreadable() throws IOException {
    JsonBackupStore store = new JsonBackupStore(dir);
    UUID id = UUID.randomUUID();
    Files.writeString(dir.resolve("garbage.json"), "{not json");
    Files.write(dir.resolve(UUID.randomUUID() + ".json"), store.serialize(snapshot(id)));
    Files.writeString(dir.resolve("ignored.txt"), "not a backup");
    RegistrationSnapshot wrongVersion =
        new RegistrationSnapshot(
            2,
            id,
            Instant.EPOCH,
            RegistrationType.EXTERNAL,
            "a",
            "b",
            "c",
            "o",
            null,
            null,
            null,
            Instant.EPOCH,
            List.of());
    Files.write(dir.resolve(id + ".json"), store.serialize(wrongVersion));

    List<JsonBackupStore.Entry> entries = store.readAll();

    assertThat(entries).hasSize(3).noneMatch(JsonBackupStore.Entry::readable);
  }

  @Test
  void deleteQuietlyNeverThrows() {
    JsonBackupStore store = new JsonBackupStore(dir.resolve("missing"));

    assertThat(store.deleteQuietly(UUID.randomUUID())).isFalse();
  }
}
