package si.konferenca.registration.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import org.springframework.stereotype.Component;
import si.konferenca.registration.config.AppProperties;

/**
 * Raw JSON copies on persistent storage (`registration-copy.schema.json`, BR-07). A copy is written
 * to a temporary file, forced to disk and moved atomically to its final name.
 */
@Component
public final class JsonCopyStore {

  private final Path directory;

  public JsonCopyStore(AppProperties app) {
    this.directory = Path.of(app.jsonCopyDir());
    try {
      Files.createDirectories(directory);
      checkWritable();
    } catch (IOException e) {
      throw new UncheckedIOException("JSON_COPY_DIR is not a writable directory", e);
    }
  }

  /** Writes the copy; returns its file name. */
  public String write(String fileName, byte[] json) throws IOException {
    Path target = directory.resolve(fileName);
    Path temporary = directory.resolve(fileName + ".tmp");
    try (FileChannel channel =
        FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      ByteBuffer buffer = ByteBuffer.wrap(json);
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
      channel.force(true);
    } catch (IOException e) {
      Files.deleteIfExists(temporary);
      throw e;
    }
    try {
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(temporary, target);
    } catch (IOException e) {
      Files.deleteIfExists(temporary);
      throw e;
    }
    return fileName;
  }

  /** Removes a copy whose database row was not committed. */
  public void delete(String fileName) throws IOException {
    Files.deleteIfExists(directory.resolve(fileName));
  }

  /** Throws when a file cannot be created in the directory (readiness, startup). */
  public void checkWritable() throws IOException {
    Path probe = Files.createTempFile(directory, ".probe-", ".tmp");
    Files.delete(probe);
  }
}
