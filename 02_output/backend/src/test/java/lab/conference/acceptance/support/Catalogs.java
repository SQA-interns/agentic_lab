package lab.conference.acceptance.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Synthetic catalog files from src/test/resources/acceptance, copied to temp files. */
public final class Catalogs {

  private Catalogs() {}

  public static Path path(String name) {
    try (InputStream in = Catalogs.class.getResourceAsStream("/acceptance/" + name)) {
      if (in == null) {
        throw new IllegalArgumentException("missing catalog resource " + name);
      }
      Path file = Files.createTempFile("catalog-", ".json");
      Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
      return file;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Overwrites target with the named resource (simulates an operator editing the file). */
  public static void replace(Path target, String name) {
    try {
      Files.copy(path(name), target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
