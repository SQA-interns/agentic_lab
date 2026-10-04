package si.konferenca.registration.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.konferenca.registration.application.JsonCopyStore;

/**
 * Writes each copy to a temporary file in the target directory and moves it into place atomically,
 * so a copy is either complete or absent (specification section 3).
 */
public final class FileJsonCopyStore implements JsonCopyStore {

  private static final Logger LOG = LoggerFactory.getLogger(FileJsonCopyStore.class);
  private static final DateTimeFormatter NAME_TIME =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);

  private final Path directory;

  public FileJsonCopyStore(Path directory) {
    this.directory = directory;
  }

  @Override
  public Path write(UUID id, Instant acceptedAt, byte[] json) {
    Path target = directory.resolve(NAME_TIME.format(acceptedAt) + "_" + id + ".json");
    Path temporary = directory.resolve("." + id + ".json.tmp");
    try {
      Files.createDirectories(directory);
      Files.write(temporary, json);
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
      return target;
    } catch (IOException e) {
      deleteQuietly(temporary);
      throw new UncheckedIOException("JSON copy could not be written", e);
    }
  }

  @Override
  public void delete(Path copy) {
    deleteQuietly(copy);
  }

  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      LOG.warn("leftover JSON copy file could not be deleted: {}", file.getFileName());
    }
  }
}
