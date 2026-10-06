package si.konferenca.registration.infrastructure.jsoncopy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.konferenca.registration.application.JsonCopyStore;
import si.konferenca.registration.domain.Registration;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Writes one durable JSON file per accepted registration (json-copy.schema.json, BR-07). */
public class FileJsonCopyStore implements JsonCopyStore {

  private static final Logger LOG = LoggerFactory.getLogger(FileJsonCopyStore.class);
  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  private final Path directory;
  private final JsonMapper mapper =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

  public FileJsonCopyStore(Path directory) {
    this.directory = directory;
  }

  @Override
  public String fileNameFor(Registration r) {
    return STAMP.format(r.receivedAt()) + "_" + r.id() + ".json";
  }

  @Override
  public byte[] serialize(Registration r) {
    Map<String, Object> participant = new LinkedHashMap<>();
    participant.put("firstName", r.firstName());
    participant.put("lastName", r.lastName());
    participant.put("email", r.email());
    putIfPresent(participant, "organization", r.organization());
    putIfPresent(participant, "studyInstitution", r.studyInstitution());
    putIfPresent(participant, "studyProgramme", r.studyProgramme());
    putIfPresent(participant, "studentId", r.studentId());
    List<Map<String, Object>> options =
        r.options().stream()
            .map(
                o -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("id", o.id());
                  m.put("name", o.name());
                  m.put("category", o.category().name());
                  return m;
                })
            .toList();
    Map<String, Object> consent = new LinkedHashMap<>();
    consent.put("id", r.consent().id());
    consent.put("text", r.consent().text());
    consent.put("givenAt", r.receivedAt().toString());
    Map<String, Object> copy = new LinkedHashMap<>();
    copy.put("schemaVersion", 1);
    copy.put("registrationId", r.id().toString());
    copy.put("receivedAt", r.receivedAt().toString());
    copy.put("type", r.type().name());
    copy.put("participant", participant);
    copy.put("options", options);
    copy.put("consent", consent);
    return mapper.writeValueAsBytes(copy);
  }

  private static void putIfPresent(Map<String, Object> map, String key, String value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  @Override
  public void write(Registration r, byte[] json) {
    Path target = directory.resolve(fileNameFor(r));
    Path temp = directory.resolve(".tmp-" + r.id());
    try {
      try (FileChannel ch =
          FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
        ByteBuffer buffer = ByteBuffer.wrap(json);
        while (buffer.hasRemaining()) {
          ch.write(buffer);
        }
        ch.force(true);
      }
      // A hard link publishes the complete file atomically and never replaces an existing copy.
      Files.createLink(target, temp);
    } catch (IOException e) {
      throw new UncheckedIOException("JSON copy not written", e);
    } finally {
      deleteQuietly(temp);
    }
  }

  @Override
  public void delete(Registration r) {
    deleteQuietly(directory.resolve(fileNameFor(r)));
  }

  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      LOG.warn("Could not remove {}: {}", file.getFileName(), e.getClass().getSimpleName());
    }
  }
}
