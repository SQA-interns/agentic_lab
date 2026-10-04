package si.konferenca.registration.acceptance.support;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/** Reads the JSON copy directory and makes it unwritable for failure tests. */
public final class JsonCopies {

  private final Path dir;
  private final Path parked;

  public JsonCopies(Path dir) {
    this.dir = dir;
    this.parked = dir.resolveSibling(dir.getFileName() + "-parked");
  }

  public Path dir() {
    return dir;
  }

  public List<Path> files() {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> s = Files.list(dir)) {
      return s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The copy of a registration: the file whose name ends with _<id>.json. */
  public Optional<Path> fileFor(UUID id) {
    return files().stream()
        .filter(p -> p.getFileName().toString().endsWith("_" + id + ".json"))
        .findFirst();
  }

  public byte[] bytes(Path file) {
    try {
      return Files.readAllBytes(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** True when any copy contains the text (for "nothing stored" checks). */
  public boolean anyContains(String text) {
    return files().stream().anyMatch(p -> new String(bytes(p), UTF_8).contains(text));
  }

  /** Replaces the directory with a plain file so that no copy can be written. */
  public void breakStorage() {
    try {
      Files.move(dir, parked);
      Files.writeString(dir, "not a directory");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Undoes {@link #breakStorage()}. */
  public void restoreStorage() {
    try {
      if (Files.isRegularFile(dir)) {
        Files.delete(dir);
      }
      if (Files.isDirectory(parked)) {
        Files.move(parked, dir);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
