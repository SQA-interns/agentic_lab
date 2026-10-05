package si.konferenca.registration.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileJsonCopyStoreTest {

  private static final UUID ID = UUID.fromString("0b9a3c4e-6a8f-4f3e-9d43-2a1f6c5e7b10");
  private static final Instant AT = Instant.parse("2026-10-05T07:08:09.012Z");

  @TempDir Path dir;

  @Test
  void writesTheCopyUnderItsTimeAndIdAndCreatesTheDirectory() throws Exception {
    Path target = dir.resolve("nested");
    Path written = new FileJsonCopyStore(target).write(ID, AT, "{\"a\":\"č\"}".getBytes(UTF_8));

    assertThat(written.getFileName().toString()).isEqualTo("20261005T070809012Z_" + ID + ".json");
    assertThat(Files.readString(written, UTF_8)).isEqualTo("{\"a\":\"č\"}");
    try (Stream<Path> files = Files.list(target)) {
      assertThat(files).containsExactly(written);
    }
  }

  @Test
  void failureLeavesNoTemporaryFile() throws Exception {
    Path notADirectory = dir.resolve("file");
    Files.writeString(notADirectory, "x");

    assertThatThrownBy(() -> new FileJsonCopyStore(notADirectory).write(ID, AT, new byte[] {1}))
        .isInstanceOf(UncheckedIOException.class);
    try (Stream<Path> files = Files.list(dir)) {
      assertThat(files).containsExactly(notADirectory);
    }
  }

  @Test
  void deleteRemovesTheCopyAndIgnoresMissingFiles() {
    FileJsonCopyStore store = new FileJsonCopyStore(dir);
    Path written = store.write(ID, AT, new byte[] {1});

    store.delete(written);
    store.delete(written);

    assertThat(written).doesNotExist();
  }
}
