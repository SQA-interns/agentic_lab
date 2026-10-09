package si.konferenca.registration.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.konferenca.registration.application.RegistrationCopyStore;
import si.konferenca.registration.domain.Registration;

/**
 * Writes each copy as {@code registration-<id>.json} in the copy directory: to a temporary file
 * first, forced to disk, then moved atomically, so a copy is either complete or absent.
 */
public final class FileRegistrationCopyStore implements RegistrationCopyStore {

  private static final Logger LOG = LoggerFactory.getLogger(FileRegistrationCopyStore.class);

  private final Path directory;

  /** Creates the directory if needed; refuses a directory that cannot be written. */
  public FileRegistrationCopyStore(Path directory) {
    this.directory = directory;
    try {
      Files.createDirectories(directory);
    } catch (IOException e) {
      throw new UncheckedIOException("JSON_COPY_DIR cannot be created", e);
    }
    if (!Files.isDirectory(directory) || !Files.isWritable(directory)) {
      throw new IllegalStateException("JSON_COPY_DIR is not a writable directory");
    }
  }

  @Override
  public byte[] write(Registration registration) {
    byte[] json = RegistrationCopyJson.toBytes(registration);
    Path target = path(registration.id());
    Path temporary = null;
    try {
      temporary = Files.createTempFile(directory, "registration-", ".tmp");
      try (FileChannel channel =
          FileChannel.open(
              temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
        channel.write(java.nio.ByteBuffer.wrap(json));
        channel.force(true);
      }
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
      return json;
    } catch (IOException | RuntimeException e) {
      deleteQuietly(temporary);
      throw new CopyStoreException("JSON copy of registration " + registration.id() + " failed", e);
    }
  }

  @Override
  public void delete(UUID registrationId) {
    deleteQuietly(path(registrationId));
  }

  private Path path(UUID registrationId) {
    return directory.resolve("registration-" + registrationId + ".json");
  }

  private static void deleteQuietly(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      LOG.warn("Could not delete {}", path.getFileName(), e);
    }
  }
}
