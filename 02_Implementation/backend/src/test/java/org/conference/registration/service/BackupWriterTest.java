package org.conference.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupWriterTest {

  @TempDir Path dir;

  @Test
  void fileNameContainsTimestampAndId() {
    UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
    assertThat(BackupWriter.fileNameFor(id, Instant.parse("2026-05-01T10:15:30.123Z")))
        .isEqualTo("registration-20260501T101530Z-11111111-2222-3333-4444-555555555555.json");
  }

  @Test
  void writesContentAtomicallyWithoutLeavingTempFile() throws Exception {
    BackupWriter writer = new BackupWriter(dir);
    String name = BackupWriter.fileNameFor(UUID.randomUUID(), Instant.now());
    byte[] content = "{\"name\":\"Žiga\"}".getBytes(StandardCharsets.UTF_8);

    Path written = writer.write(name, content);

    assertThat(written).hasFileName(name);
    assertThat(Files.readAllBytes(written)).isEqualTo(content);
    try (var files = Files.list(dir)) {
      assertThat(files).containsExactly(written);
    }
  }

  @Test
  void deleteRemovesBackup() {
    BackupWriter writer = new BackupWriter(dir);
    String name = BackupWriter.fileNameFor(UUID.randomUUID(), Instant.now());
    Path written = writer.write(name, new byte[] {1});
    writer.delete(name);
    assertThat(written).doesNotExist();
  }

  @Test
  void writingExistingFileFailsWithBackupFailedException() {
    BackupWriter writer = new BackupWriter(dir);
    String name = BackupWriter.fileNameFor(UUID.randomUUID(), Instant.now());
    writer.write(name, new byte[] {1});
    assertThatThrownBy(() -> writer.write(name, new byte[] {2}))
        .isInstanceOf(BackupFailedException.class);
  }

  @Test
  void rejectsPathTraversalNames() {
    BackupWriter writer = new BackupWriter(dir);
    assertThatThrownBy(() -> writer.write("../evil.json", new byte[] {1}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> writer.delete("..\\evil.json"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createsMissingDirectory() {
    Path nested = dir.resolve("a").resolve("b");
    new BackupWriter(nested);
    assertThat(nested).isDirectory();
  }

  @Test
  void failsStartupWhenDirectoryCannotBeCreated() throws Exception {
    Path file = Files.createFile(dir.resolve("not-a-dir"));
    assertThatThrownBy(() -> new BackupWriter(file.resolve("sub")))
        .isInstanceOf(IllegalStateException.class);
  }
}
