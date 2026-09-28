package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import si.konferenca.registration.config.AppProperties;

class FileSystemRegistrationBackupStoreTest {

  @TempDir Path tempDir;

  private static AppProperties properties(Path dir) {
    return new AppProperties(
        new AppProperties.Recaptcha(true, null, null, "http://localhost"),
        new AppProperties.Mail("from@test", List.of()),
        new AppProperties.Backup(dir.toString()),
        new AppProperties.Options(null),
        new AppProperties.Organizer("organizer", null),
        new AppProperties.RateLimit(
            new AppProperties.Limit(10, 600), new AppProperties.Limit(30, 600)),
        new AppProperties.Request(16384));
  }

  @Test
  void createsDirectoryAndWritesJsonFileNamedByTimestampAndId() throws Exception {
    Path dir = tempDir.resolve("nested/backups");
    FileSystemRegistrationBackupStore store =
        new FileSystemRegistrationBackupStore(properties(dir));
    UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
    Instant createdAt = Instant.parse("2026-03-01T10:20:30.123Z");
    byte[] json = "{\"firstName\":\"Žiga\"}".getBytes(StandardCharsets.UTF_8);

    store.store(id, createdAt, json);

    Path file = dir.resolve("20260301T102030Z_11111111-2222-3333-4444-555555555555.json");
    assertThat(file).exists();
    assertThat(Files.readString(file, StandardCharsets.UTF_8))
        .isEqualTo("{\"firstName\":\"Žiga\"}");
    try (Stream<Path> files = Files.list(dir)) {
      assertThat(files.toList()).hasSize(1); // no temp file left behind
    }
  }

  @Test
  void deleteRemovesBackup() throws Exception {
    FileSystemRegistrationBackupStore store =
        new FileSystemRegistrationBackupStore(properties(tempDir));
    UUID id = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-03-01T10:20:30Z");
    store.store(id, createdAt, new byte[] {'{', '}'});

    store.delete(id, createdAt);
    store.delete(id, createdAt); // idempotent

    try (Stream<Path> files = Files.list(tempDir)) {
      assertThat(files.toList()).isEmpty();
    }
  }

  @Test
  void failsWhenBackupCannotBeWritten() throws Exception {
    Path dir = tempDir.resolve("backups");
    FileSystemRegistrationBackupStore store =
        new FileSystemRegistrationBackupStore(properties(dir));
    Files.delete(dir);
    Files.writeString(dir, "not a directory");

    assertThatThrownBy(() -> store.store(UUID.randomUUID(), Instant.now(), new byte[] {1}))
        .isInstanceOf(UncheckedIOException.class);
  }
}
