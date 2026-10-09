package si.konferenca.registration.adapter.jsoncopy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import si.konferenca.registration.application.JsonCopyStore;
import si.konferenca.registration.application.StorageException;
import si.konferenca.registration.domain.Field;
import si.konferenca.registration.domain.Registration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes {@code <dir>/<id>.json} (docs/02_contracts/registration-copy.schema.json): temporary file,
 * flush to disk, atomic rename; deleted again if the surrounding transaction rolls back (AR-05).
 */
public class FileJsonCopyStore implements JsonCopyStore {

  private static final Logger LOG = LoggerFactory.getLogger(FileJsonCopyStore.class);
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Path directory;

  public FileJsonCopyStore(Path directory) {
    this.directory = directory;
  }

  @Override
  public byte[] write(Registration registration) {
    byte[] bytes = JSON.writeValueAsBytes(document(registration));
    Path target = directory.resolve(registration.id() + ".json");
    Path temporary = null;
    try {
      temporary = Files.createTempFile(directory, ".tmp-", ".json");
      try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
        channel.force(true);
      }
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException | RuntimeException e) {
      deleteQuietly(temporary);
      throw new StorageException("JSON copy could not be written", e);
    }
    deleteOnRollback(target, registration);
    return bytes;
  }

  private static void deleteOnRollback(Path target, Registration registration) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
              deleteQuietly(target);
              LOG.warn("Registration {} rolled back; JSON copy removed", registration.id());
            }
          }
        });
  }

  private static void deleteQuietly(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      LOG.error("Could not delete {}", path.getFileName(), e);
    }
  }

  static Map<String, Object> document(Registration registration) {
    Map<String, Object> participant = new LinkedHashMap<>();
    for (Field field : Field.of(registration.type())) {
      participant.put(field.apiName(), registration.value(field));
    }
    List<Map<String, Object>> options = new ArrayList<>();
    registration
        .selectedOptions()
        .forEach(
            o -> {
              Map<String, Object> option = new LinkedHashMap<>();
              option.put("id", o.id());
              option.put("name", o.name());
              option.put("category", o.category().name());
              options.add(option);
            });
    List<Map<String, Object>> consents = new ArrayList<>();
    registration
        .consents()
        .forEach(
            c -> {
              Map<String, Object> consent = new LinkedHashMap<>();
              consent.put("id", c.id());
              consent.put("text", c.text());
              consent.put("givenAt", c.givenAt().toString());
              consents.add(consent);
            });
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("schemaVersion", 1);
    document.put("id", registration.id().toString());
    document.put("type", registration.type().name());
    document.put("submittedAt", registration.submittedAt().toString());
    document.put("participant", participant);
    document.put("selectedOptions", options);
    document.put("consents", consents);
    return document;
  }
}
