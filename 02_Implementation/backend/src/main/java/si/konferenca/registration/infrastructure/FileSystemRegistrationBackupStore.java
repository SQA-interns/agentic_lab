package si.konferenca.registration.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.service.RegistrationBackupStore;

/** Writes raw JSON registration backups atomically to a directory on persistent storage. */
@Component
public class FileSystemRegistrationBackupStore implements RegistrationBackupStore {

  private static final Logger LOG =
      LoggerFactory.getLogger(FileSystemRegistrationBackupStore.class);
  private static final DateTimeFormatter FILE_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  private final Path directory;

  public FileSystemRegistrationBackupStore(AppProperties properties) {
    this.directory = Path.of(properties.backup().dir()).toAbsolutePath().normalize();
    try {
      Files.createDirectories(directory);
    } catch (IOException e) {
      throw new UncheckedIOException("Backup directory cannot be created", e);
    }
  }

  @Override
  public void store(UUID registrationId, Instant createdAt, byte[] json) {
    Path target = fileFor(registrationId, createdAt);
    Path temp = target.resolveSibling(target.getFileName() + ".tmp");
    try {
      Files.write(temp, json);
      try {
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      deleteQuietly(temp);
      throw new UncheckedIOException("Registration backup could not be written", e);
    }
  }

  @Override
  public void delete(UUID registrationId, Instant createdAt) {
    deleteQuietly(fileFor(registrationId, createdAt));
  }

  Path fileFor(UUID registrationId, Instant createdAt) {
    return directory.resolve(FILE_TIMESTAMP.format(createdAt) + "_" + registrationId + ".json");
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      LOG.warn("Backup file could not be deleted: {}", path.getFileName());
    }
  }
}
