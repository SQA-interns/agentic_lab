package si.konferenca.registration.infrastructure.copy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.konferenca.registration.application.RegistrationPorts.JsonCopyStore;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Raw JSON copies as {@code <id>.json} in JSON_COPY_DIR
 * (docs/02_contracts/registration-copy.schema.json), written to a temporary file in the same
 * directory and moved into place atomically.
 */
public class FileJsonCopyStore implements JsonCopyStore {

  private static final Logger LOG = LoggerFactory.getLogger(FileJsonCopyStore.class);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Path directory;

  public FileJsonCopyStore(Path directory) {
    this.directory = directory;
  }

  /** Creates the directory at startup if it does not exist. */
  public void prepare() {
    try {
      Files.createDirectories(directory);
    } catch (IOException e) {
      throw new IllegalStateException("JSON_COPY_DIR cannot be created", e);
    }
  }

  @Override
  public byte[] write(Registration registration) {
    byte[] content = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(toJson(registration));
    Path target = file(registration.id());
    try {
      Path temporary = Files.createTempFile(directory, ".tmp-" + registration.id(), ".json");
      try {
        Files.write(temporary, content);
        try {
          Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
          Files.move(temporary, target);
        }
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("JSON copy could not be written", e);
    }
    return content;
  }

  @Override
  public void delete(UUID registrationId) {
    try {
      Files.deleteIfExists(file(registrationId));
    } catch (IOException e) {
      LOG.error("JSON copy of registration {} could not be removed", registrationId, e);
    }
  }

  private Path file(UUID registrationId) {
    return directory.resolve(registrationId + ".json");
  }

  static Map<String, Object> toJson(Registration r) {
    Participant p = r.participant();
    Map<String, Object> participant = new LinkedHashMap<>();
    participant.put("firstName", p.firstName());
    participant.put("lastName", p.lastName());
    participant.put("email", p.email());
    putIfPresent(participant, "organization", p.organization());
    putIfPresent(participant, "studyInstitution", p.studyInstitution());
    putIfPresent(participant, "studyProgramme", p.studyProgramme());
    putIfPresent(participant, "studentId", p.studentId());
    List<Map<String, Object>> options =
        r.options().stream()
            .map(
                o -> {
                  Map<String, Object> option = new LinkedHashMap<>();
                  option.put("id", o.id());
                  option.put("name", o.name());
                  option.put("category", o.category().value());
                  return option;
                })
            .toList();
    List<Map<String, Object>> consents =
        r.consents().stream()
            .map(
                c -> {
                  Map<String, Object> consent = new LinkedHashMap<>();
                  consent.put("id", c.id());
                  consent.put("text", c.text());
                  consent.put("givenAt", c.givenAt().toString());
                  return consent;
                })
            .toList();
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("schemaVersion", 1);
    json.put("registrationId", r.id().toString());
    json.put("type", r.type().value());
    json.put("receivedAt", r.receivedAt().toString());
    json.put("participant", participant);
    json.put("options", options);
    json.put("consents", consents);
    return json;
  }

  private static void putIfPresent(Map<String, Object> map, String key, String value) {
    if (value != null) {
      map.put(key, value);
    }
  }
}
