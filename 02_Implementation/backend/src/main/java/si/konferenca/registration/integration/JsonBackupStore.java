package si.konferenca.registration.integration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import si.konferenca.registration.domain.RegistrationSnapshot;

/**
 * Raw JSON registration backups on persistent storage (specification §10): one file per
 * registration, written atomically.
 */
public class JsonBackupStore {

  private static final String SUFFIX = ".json";

  private final Path directory;
  private final ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .enable(SerializationFeature.INDENT_OUTPUT)
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

  public JsonBackupStore(Path directory) {
    this.directory = directory;
  }

  /** Creates the backup directory if it does not exist yet (called once at startup). */
  public void initialize() throws IOException {
    Files.createDirectories(directory);
  }

  /** The exact bytes stored in the backup file and attached to the organizer email. */
  public byte[] serialize(RegistrationSnapshot snapshot) throws IOException {
    return mapper.writeValueAsBytes(snapshot);
  }

  /** Writes {@code <id>.json} via a synced temporary file and an atomic rename. */
  public void write(UUID id, byte[] content) throws IOException {
    Path target = fileOf(id);
    Path temp = directory.resolve(id + SUFFIX + ".tmp");
    try {
      try (FileChannel channel =
          FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
        ByteBuffer buffer = ByteBuffer.wrap(content);
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
        channel.force(true);
      }
      try {
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  /** Removes a backup whose registration was not committed. Never throws. */
  public boolean deleteQuietly(UUID id) {
    try {
      return Files.deleteIfExists(fileOf(id));
    } catch (IOException | RuntimeException e) {
      return false;
    }
  }

  public Path fileOf(UUID id) {
    return directory.resolve(id + SUFFIX);
  }

  /** Result of reading one backup file: either a snapshot or a failure. */
  public record Entry(Path file, RegistrationSnapshot snapshot) {
    public boolean readable() {
      return snapshot != null;
    }
  }

  /**
   * Reads every {@code *.json} backup; unreadable or invalid files yield an entry without snapshot.
   */
  public List<Entry> readAll() throws IOException {
    List<Entry> entries = new ArrayList<>();
    try (Stream<Path> files = Files.list(directory)) {
      for (Path file :
          files.filter(p -> String.valueOf(p.getFileName()).endsWith(SUFFIX)).sorted().toList()) {
        entries.add(new Entry(file, readValid(file)));
      }
    }
    return entries;
  }

  private RegistrationSnapshot readValid(Path file) {
    try {
      RegistrationSnapshot s =
          mapper.readValue(Files.readAllBytes(file), RegistrationSnapshot.class);
      boolean complete =
          s.schemaVersion() == RegistrationSnapshot.SCHEMA_VERSION
              && s.registrationId() != null
              && s.submittedAt() != null
              && s.type() != null
              && s.firstName() != null
              && s.lastName() != null
              && s.email() != null
              && s.personalDataConsentAt() != null
              && s.options() != null
              && s.options().stream()
                  .allMatch(o -> o.id() != null && o.category() != null && o.name() != null)
              && String.valueOf(file.getFileName()).equals(s.registrationId() + SUFFIX);
      return complete ? s : null;
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }
}
