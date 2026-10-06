package si.konferenca.registration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import si.konferenca.registration.config.AppProperties;

class JsonCopyStoreTest {

  @TempDir Path dir;

  private static AppProperties settings(Path directory) {
    return new AppProperties("test", "C", "", directory.toString(), null, null, null, "", null, 1);
  }

  private static long files(Path directory) throws IOException {
    try (Stream<Path> list = Files.list(directory)) {
      return list.count();
    }
  }

  @Test
  void writesTheBytesUnderTheFinalNameAndLeavesNoTemporaryFile() throws IOException {
    JsonCopyStore store = new JsonCopyStore(settings(dir.resolve("copies")));
    byte[] json = "{\"a\":\"Špela\"}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    assertThat(store.write("x.json", json)).isEqualTo("x.json");

    assertThat(Files.readAllBytes(dir.resolve("copies/x.json"))).isEqualTo(json);
    assertThat(files(dir.resolve("copies"))).isEqualTo(1);
    store.delete("x.json");
    assertThat(files(dir.resolve("copies"))).isZero();
  }

  @Test
  void refusesToOverwriteAnExistingCopyAndCleansUp() throws IOException {
    JsonCopyStore store = new JsonCopyStore(settings(dir));
    store.write("x.json", new byte[] {1});
    Files.writeString(dir.resolve("y.json.tmp"), "stale");

    assertThatThrownBy(() -> store.write("y.json", new byte[] {2})).isInstanceOf(IOException.class);
    assertThat(Files.exists(dir.resolve("y.json"))).isFalse();
  }

  @Test
  void failsInAReadOnlyDirectoryAndReportsItUnhealthy() throws IOException {
    JsonCopyStore store = new JsonCopyStore(settings(dir));
    JsonCopyDirectoryHealthIndicator health = new JsonCopyDirectoryHealthIndicator(store);
    Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-x------"));
    try {
      assertThatThrownBy(() -> store.write("z.json", new byte[] {1}))
          .isInstanceOf(IOException.class);
      assertThat(health.health().getStatus().getCode()).isEqualTo("DOWN");
    } finally {
      Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
    }
    assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
  }

  @Test
  void refusesToStartWithAnUnwritableDirectory() throws IOException {
    Path file = Files.writeString(dir.resolve("not-a-dir"), "x");

    assertThatThrownBy(() -> new JsonCopyStore(settings(file)))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("JSON_COPY_DIR");
  }
}
