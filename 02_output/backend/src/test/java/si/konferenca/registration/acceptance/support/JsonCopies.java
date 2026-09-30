package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Reads JSON copies from the configured directory (02_contracts/registration-copy.schema.json). */
public final class JsonCopies {

  private JsonCopies() {}

  public static Path copyOf(Path dir, String registrationId) {
    return dir.resolve(registrationId + ".json");
  }

  /** Every file in the directory whose content mentions the text (for example an email). */
  public static List<Path> containing(Path dir, String text) {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files.filter(Files::isRegularFile).filter(f -> read(f).contains(text)).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
