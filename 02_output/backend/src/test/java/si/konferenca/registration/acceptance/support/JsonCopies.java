package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;

/** Reads the raw JSON copies (docs/02_contracts/registration-copy.schema.json). */
public final class JsonCopies {

  private JsonCopies() {}

  public static Path dir() {
    return TestEnvironment.JSON_COPY_DIR;
  }

  /** Regular files in the copy directory, temporary files included. */
  public static List<Path> files() {
    if (!Files.isDirectory(dir())) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(dir())) {
      return files.filter(Files::isRegularFile).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Path fileFor(String registrationId) {
    return dir().resolve(registrationId + ".json");
  }

  public static byte[] bytes(String registrationId) {
    try {
      return Files.readAllBytes(fileFor(registrationId));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static JsonNode read(String registrationId) {
    return Api.JSON.readTree(bytes(registrationId));
  }

  /** Empties the copy directory, recreating it if a test replaced it. */
  public static void reset() {
    try {
      if (Files.exists(dir()) && !Files.isDirectory(dir())) {
        Files.delete(dir());
      }
      Files.createDirectories(dir());
      for (Path file : files()) {
        Files.delete(file);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Replaces the copy directory by a regular file, so no copy can be written into it. */
  public static void makeUnwritable() {
    try {
      reset();
      Files.delete(dir());
      Files.writeString(dir(), "not a directory");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
