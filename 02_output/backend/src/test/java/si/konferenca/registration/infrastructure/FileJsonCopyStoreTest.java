package si.konferenca.registration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import si.konferenca.registration.application.RegistrationCopy;
import si.konferenca.registration.application.StorageException;

class FileJsonCopyStoreTest {

  @TempDir Path dir;

  static RegistrationCopy copy() {
    Instant at = Instant.parse("2026-09-30T10:00:00.123Z");
    return new RegistrationCopy(
        1,
        UUID.fromString("11111111-2222-3333-4444-555555555555"),
        at,
        "EXTERNAL",
        "Žan",
        "Čuk",
        "zan@example.si",
        "Šola",
        null,
        null,
        null,
        List.of(new RegistrationCopy.Option("ws", "Delavnica", "workshop")),
        List.of(new RegistrationCopy.Consent("privacy", at)));
  }

  @Test
  void writesUtf8JsonInSchemaOrderWithoutOtherTypeFields() throws IOException {
    Path target = dir.resolve("nested");
    FileJsonCopyStore store = new FileJsonCopyStore(TestProperties.with("x", target.toString()));

    byte[] written = store.write(copy());

    Path file = target.resolve("11111111-2222-3333-4444-555555555555.json");
    assertThat(Files.readAllBytes(file)).isEqualTo(written);
    String json = new String(written, StandardCharsets.UTF_8);
    assertThat(json).startsWith("{");
    assertThat(json.indexOf("\"schemaVersion\"")).isLessThan(json.indexOf("\"id\""));
    assertThat(json.indexOf("\"email\"")).isLessThan(json.indexOf("\"organization\""));
    assertThat(json).contains("\"Žan\"").contains("\"2026-09-30T10:00:00.123Z\"");
    assertThat(json).doesNotContain("studentId").doesNotContain("studyInstitution");
    try (var files = Files.list(target)) {
      assertThat(files.toList()).hasSize(1);
    }
  }

  @Test
  void deleteRemovesTheCopyAndIgnoresMissingFiles() {
    FileJsonCopyStore store = new FileJsonCopyStore(TestProperties.with("x", dir.toString()));
    store.write(copy());

    store.delete(copy().id());
    store.delete(copy().id());

    assertThat(dir.resolve(copy().id() + ".json")).doesNotExist();
  }

  @Test
  void failsWithStorageExceptionWhenDirectoryIsUnusable() throws IOException {
    Path blocker = Files.writeString(dir.resolve("file"), "x");
    FileJsonCopyStore store =
        new FileJsonCopyStore(TestProperties.with("x", blocker.resolve("sub").toString()));

    assertThatThrownBy(() -> store.write(copy()))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining(copy().id().toString());
  }
}
