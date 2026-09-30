package si.konferenca.registration.infrastructure;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import si.konferenca.registration.application.JsonCopyStore;
import si.konferenca.registration.application.RegistrationCopy;
import si.konferenca.registration.application.StorageException;
import si.konferenca.registration.settings.AppProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes JSON copies to the configured directory: a temporary file first, then an atomic move to
 * {@code <id>.json}, so a copy is never half written. The directory is created on first use.
 */
@Component
public class FileJsonCopyStore implements JsonCopyStore {

  private static final Logger LOG = LoggerFactory.getLogger(FileJsonCopyStore.class);

  private final Path directory;
  private final JsonMapper mapper = JsonMapper.builder().build();

  public FileJsonCopyStore(AppProperties properties) {
    this.directory = Path.of(properties.jsonCopyDir());
  }

  @Override
  public byte[] write(RegistrationCopy copy) {
    byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(copy);
    Path target = directory.resolve(copy.id() + ".json");
    try {
      Files.createDirectories(directory);
      Path temp = Files.createTempFile(directory, copy.id().toString(), ".tmp");
      try {
        Files.write(temp, bytes);
        move(temp, target);
      } finally {
        Files.deleteIfExists(temp);
      }
      return bytes;
    } catch (IOException e) {
      throw new StorageException("JSON copy for registration " + copy.id() + " not written", e);
    }
  }

  @Override
  public void delete(UUID id) {
    try {
      Files.deleteIfExists(directory.resolve(id + ".json"));
    } catch (IOException e) {
      LOG.error("JSON copy for registration {} could not be removed", id, e);
    }
  }

  private static void move(Path from, Path to) throws IOException {
    try {
      Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
