package si.konferenca.registration.infrastructure;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.konferenca.registration.application.JsonCopyStore;
import si.konferenca.registration.application.StorageException;
import si.konferenca.registration.domain.Registration;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the raw JSON copy of each registration to {@code <dir>/<reference>.json}
 * (registration-copy.schema.json), via a temporary file and an atomic move.
 */
public class FileJsonCopyStore implements JsonCopyStore {

  private static final Logger LOG = LoggerFactory.getLogger(FileJsonCopyStore.class);

  private final Path directory;
  private final JsonMapper mapper =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

  public FileJsonCopyStore(Path directory) {
    this.directory = directory;
  }

  @Override
  public void write(Registration r) {
    Path target = file(r.reference());
    Path temp = null;
    try {
      Files.createDirectories(directory);
      temp = Files.createTempFile(directory, ".tmp-", ".json");
      Files.write(temp, mapper.writeValueAsBytes(document(r)));
      move(temp, target);
    } catch (IOException | RuntimeException e) {
      deleteQuietly(temp);
      throw new StorageException("JSON copy could not be written", e);
    }
  }

  @Override
  public byte[] read(UUID reference) {
    try {
      return Files.readAllBytes(file(reference));
    } catch (IOException e) {
      throw new StorageException("JSON copy could not be read", e);
    }
  }

  @Override
  public void delete(UUID reference) {
    deleteQuietly(file(reference));
  }

  @Override
  public boolean writable() {
    try {
      Files.createDirectories(directory);
      return Files.isDirectory(directory) && Files.isWritable(directory);
    } catch (IOException | SecurityException e) {
      return false;
    }
  }

  /** The copy document, with properties in schema order. */
  static Map<String, Object> document(Registration r) {
    Map<String, Object> participant = new LinkedHashMap<>();
    participant.put("firstName", r.firstName());
    participant.put("lastName", r.lastName());
    participant.put("email", r.email());
    putIfPresent(participant, "organization", r.organization());
    putIfPresent(participant, "studyInstitution", r.studyInstitution());
    putIfPresent(participant, "studyProgramme", r.studyProgramme());
    putIfPresent(participant, "studentId", r.studentId());
    List<Map<String, Object>> options = new ArrayList<>();
    r.options()
        .forEach(
            o -> {
              Map<String, Object> option = new LinkedHashMap<>();
              option.put("id", o.optionId());
              option.put("name", o.optionName());
              option.put("category", o.category().name());
              options.add(option);
            });
    List<Map<String, Object>> consents = new ArrayList<>();
    r.consents()
        .forEach(
            c -> {
              Map<String, Object> consent = new LinkedHashMap<>();
              consent.put("id", c.consentId());
              consent.put("text", c.consentText());
              consent.put("givenAt", c.givenAt().toString());
              consents.add(consent);
            });
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("schemaVersion", 1);
    doc.put("reference", r.reference().toString());
    doc.put("type", r.type().name());
    doc.put("submittedAt", r.submittedAt().toString());
    doc.put("participant", participant);
    doc.put("options", options);
    doc.put("consents", consents);
    return doc;
  }

  private Path file(UUID reference) {
    return directory.resolve(reference + ".json");
  }

  private static void putIfPresent(Map<String, Object> map, String key, String value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  private static void move(Path from, Path to) throws IOException {
    try {
      Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void deleteQuietly(Path file) {
    if (file == null) {
      return;
    }
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      LOG.warn("Could not delete an uncommitted JSON copy file");
    }
  }
}
