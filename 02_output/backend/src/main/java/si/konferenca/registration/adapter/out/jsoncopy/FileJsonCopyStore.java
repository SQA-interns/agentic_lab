package si.konferenca.registration.adapter.out.jsoncopy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import si.konferenca.registration.domain.JsonCopyStore;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.Registration.SelectedOption;
import si.konferenca.registration.domain.TextField;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * One UTF-8 file {@code <id>.json} per registration in the configured directory
 * (registration-copy.schema.json). A copy is written to a temporary file, forced to disk and then
 * moved into place, so a reader never sees a partial copy.
 */
public class FileJsonCopyStore implements JsonCopyStore {

  private static final int SCHEMA_VERSION = 1;

  private final Path directory;
  private final JsonMapper json = JsonMapper.builder().build();

  private FileJsonCopyStore(Path directory) {
    this.directory = directory;
  }

  /** Opens the store and creates the directory if it does not exist yet. */
  public static FileJsonCopyStore open(Path directory) {
    try {
      Files.createDirectories(directory);
    } catch (IOException e) {
      throw new UncheckedIOException("The JSON copy directory cannot be created", e);
    }
    return new FileJsonCopyStore(directory);
  }

  @Override
  public void write(Registration registration) {
    byte[] content = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(toJson(registration));
    Path target = file(registration.id());
    Path temporary = temporaryFile(registration.id());
    try {
      try (FileChannel channel =
          FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
        ByteBuffer buffer = ByteBuffer.wrap(content);
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
        channel.force(true);
      }
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      delete(registration.id());
      throw new UncheckedIOException("The JSON copy could not be written", e);
    }
  }

  @Override
  public byte[] read(UUID id) {
    try {
      return Files.readAllBytes(file(id));
    } catch (IOException e) {
      throw new UncheckedIOException("The JSON copy could not be read", e);
    }
  }

  @Override
  public void delete(UUID id) {
    for (Path path : new Path[] {temporaryFile(id), file(id)}) {
      try {
        Files.deleteIfExists(path);
      } catch (IOException | RuntimeException e) {
        // Nothing more can be done here; the caller reports the registration as not stored.
        continue;
      }
    }
  }

  private Path file(UUID id) {
    return directory.resolve(id + ".json");
  }

  private Path temporaryFile(UUID id) {
    return directory.resolve(id + ".json.tmp");
  }

  private ObjectNode toJson(Registration registration) {
    ObjectNode root = json.createObjectNode();
    root.put("schemaVersion", SCHEMA_VERSION);
    root.put("id", registration.id().toString());
    root.put("type", registration.type().name());
    root.put("acceptedAt", registration.acceptedAt().toString());
    for (TextField field : registration.type().fields()) {
      root.put(field.apiName(), registration.value(field));
    }
    ArrayNode options = root.putArray("options");
    for (SelectedOption option : registration.options()) {
      ObjectNode node = options.addObject();
      node.put("id", option.id());
      node.put("name", option.name());
      node.put("category", option.category().code());
    }
    ObjectNode consent = root.putObject("consent");
    consent.put("id", registration.consent().id());
    consent.put("text", registration.consent().text());
    consent.put("givenAt", registration.consent().givenAt().toString());
    return root;
  }
}
