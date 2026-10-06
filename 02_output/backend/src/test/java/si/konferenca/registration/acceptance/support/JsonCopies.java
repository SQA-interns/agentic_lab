package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Stream;

/** The raw JSON copy directory (`registration-copy.schema.json`). */
public final class JsonCopies {

  private final Path dir;

  public JsonCopies(Path dir) {
    this.dir = dir;
  }

  public Path dir() {
    return dir;
  }

  /** Every regular file in the directory, sorted by name. */
  public List<Path> files() {
    try (Stream<Path> files = Files.list(dir)) {
      return files.filter(Files::isRegularFile).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Every completed copy (`*.json`), sorted by name. */
  public List<Path> copies() {
    return files().stream().filter(p -> p.getFileName().toString().endsWith(".json")).toList();
  }

  public void clear() {
    setWritable(true);
    for (Path file : files()) {
      try {
        Files.delete(file);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  /** Failure injection: a read-only directory makes every new copy fail to be written. */
  public void setWritable(boolean writable) {
    try {
      Files.setPosixFilePermissions(
          dir, PosixFilePermissions.fromString(writable ? "rwx------" : "r-x------"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
