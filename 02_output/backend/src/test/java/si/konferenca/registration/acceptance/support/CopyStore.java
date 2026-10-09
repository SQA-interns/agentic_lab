package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON copy directory (docs/02_contracts/registration-copy.schema.json): reads copies and makes
 * the directory unwritable by replacing it with a plain file.
 */
public final class CopyStore {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Path dir;

  public CopyStore(Path dir) {
    this.dir = dir;
  }

  public Path dir() {
    return dir;
  }

  /** Restores an empty, writable directory. */
  public void clear() {
    try {
      if (Files.isRegularFile(dir)) {
        Files.delete(dir);
      }
      if (Files.isDirectory(dir)) {
        try (Stream<Path> paths = Files.walk(dir)) {
          for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
            if (!path.equals(dir)) {
              Files.delete(path);
            }
          }
        }
      }
      Files.createDirectories(dir);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Replaces the directory with a regular file, so no copy can be written into it. */
  public void makeUnwritable() {
    clear();
    try {
      Files.delete(dir);
      Files.writeString(dir, "not a directory");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Names of the files in the directory, sorted; empty when it is not a directory. */
  public List<String> fileNames() {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> paths = Files.list(dir)) {
      return paths.map(path -> path.getFileName().toString()).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public Path copyOf(String registrationId) {
    return dir.resolve("registration-" + registrationId + ".json");
  }

  public byte[] bytesOf(String registrationId) {
    try {
      return Files.readAllBytes(copyOf(registrationId));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public JsonNode jsonOf(String registrationId) {
    return JSON.readTree(bytesOf(registrationId));
  }
}
