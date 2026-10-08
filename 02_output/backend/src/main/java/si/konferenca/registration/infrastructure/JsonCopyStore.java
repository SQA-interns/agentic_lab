package si.konferenca.registration.infrastructure;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Raw JSON copies on persistent storage (registration-copy.schema.json, BR-07). A copy is written
 * to a temporary file, flushed to disk and moved into place atomically.
 */
@Component
public final class JsonCopyStore {

  private final Path directory;
  private final JsonMapper mapper = JsonMapper.builder().build();

  public JsonCopyStore(AppProperties properties) throws IOException {
    this.directory = Path.of(properties.jsonCopyDir());
    Files.createDirectories(directory);
  }

  /** Writes the copy; any failure is an {@link IOException} and leaves no copy behind. */
  public void write(Registration registration) throws IOException {
    byte[] content = mapper.writeValueAsBytes(document(registration));
    Path target = path(registration.id());
    Path temp = directory.resolve(registration.id() + ".tmp");
    try (FileChannel channel =
        FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      ByteBuffer buffer = ByteBuffer.wrap(content);
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
      channel.force(true);
      Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      Files.deleteIfExists(temp);
      throw e;
    }
  }

  /** The stored bytes, as attached to the organizer email (AC-007-02). */
  public byte[] read(UUID id) throws IOException {
    return Files.readAllBytes(path(id));
  }

  /** Deletes the copy; returns false if there was none. */
  public boolean delete(UUID id) throws IOException {
    return Files.deleteIfExists(path(id));
  }

  private Path path(UUID id) {
    return directory.resolve(id + ".json");
  }

  static Map<String, Object> document(Registration r) {
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("schemaVersion", 1);
    doc.put("id", r.id().toString());
    doc.put("receivedAt", r.receivedAt().toString());
    doc.put("type", r.type().name());
    doc.put("participant", participant(r.participant()));
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
    doc.put("options", options);
    List<Map<String, Object>> consents =
        r.consents().stream()
            .map(
                c -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("id", c.id());
                  m.put("text", c.text());
                  m.put("givenAt", c.givenAt().toString());
                  return m;
                })
            .toList();
    doc.put("consents", consents);
    return doc;
  }

  private static Map<String, Object> participant(Participant p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("firstName", p.firstName());
    m.put("lastName", p.lastName());
    m.put("email", p.email());
    putIfPresent(m, "organization", p.organization());
    putIfPresent(m, "studyInstitution", p.studyInstitution());
    putIfPresent(m, "studyProgramme", p.studyProgramme());
    putIfPresent(m, "studentId", p.studentId());
    return m;
  }

  private static void putIfPresent(Map<String, Object> m, String key, String value) {
    if (value != null) {
      m.put(key, value);
    }
  }
}
