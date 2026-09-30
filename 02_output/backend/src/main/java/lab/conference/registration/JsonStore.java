package lab.conference.registration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Durable raw JSON store (AR-04, SR-02). File names derive only from server-generated UUIDs.
 * Publication is atomic: write + fsync a staging file, atomic rename into {@code registrations/},
 * fsync the directory.
 */
public final class JsonStore {

  private static final Logger LOG = LoggerFactory.getLogger(JsonStore.class);

  private final Path registrations;
  private final Path staging;
  private final Path orphaned;
  private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

  public JsonStore(Path root) throws IOException {
    this.registrations = root.resolve("registrations");
    this.staging = root.resolve("staging");
    this.orphaned = root.resolve("orphaned");
    for (Path dir : List.of(registrations, staging, orphaned)) {
      Files.createDirectories(dir);
      if (!Files.isWritable(dir)) {
        throw new IOException("JSON store directory is not writable");
      }
    }
  }

  public Path file(UUID id) {
    return registrations.resolve(id + ".json");
  }

  /** Publishes the bytes; the id stays "in flight" until {@link #settled(UUID)}. */
  public void publish(UUID id, byte[] bytes) throws IOException {
    inFlight.add(id);
    Path tmp = staging.resolve(id + ".json.tmp");
    try {
      try (FileChannel ch =
          FileChannel.open(
              tmp,
              StandardOpenOption.CREATE_NEW,
              StandardOpenOption.WRITE,
              StandardOpenOption.DSYNC)) {
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        while (buf.hasRemaining()) {
          ch.write(buf);
        }
        ch.force(true);
      }
      try {
        Files.move(tmp, file(id), StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        throw new IOException("JSON store must support atomic rename", e);
      }
      fsyncDirectory(registrations);
    } catch (IOException e) {
      Files.deleteIfExists(tmp);
      inFlight.remove(id);
      throw e;
    }
  }

  /** Marks the publication as settled (committed or rolled back). */
  public void settled(UUID id) {
    inFlight.remove(id);
  }

  /** Removes a published file whose database commit failed. */
  public void discard(UUID id) {
    try {
      Files.deleteIfExists(file(id));
    } catch (IOException e) {
      LOG.warn("Could not remove unaccepted JSON {}; reconciliation will quarantine it", id);
    } finally {
      inFlight.remove(id);
    }
  }

  public byte[] read(UUID id) throws IOException {
    return Files.readAllBytes(file(id));
  }

  /** Published files older than {@code cutoff} that are not in flight. */
  public List<UUID> settledFilesOlderThan(Instant cutoff) throws IOException {
    List<UUID> ids = new ArrayList<>();
    try (DirectoryStream<Path> dir = Files.newDirectoryStream(registrations, "*.json")) {
      for (Path p : dir) {
        UUID id = idOf(p, ".json");
        if (id != null && !inFlight.contains(id) && modifiedBefore(p, cutoff)) {
          ids.add(id);
        }
      }
    }
    return ids;
  }

  /** Moves a file without a database row out of the accepted set. */
  public void quarantine(UUID id) throws IOException {
    if (!inFlight.contains(id)) {
      Files.move(file(id), orphaned.resolve(id + ".json"), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /** Deletes abandoned staging files; returns how many were removed. */
  public int cleanStaging(Instant cutoff) throws IOException {
    int removed = 0;
    try (DirectoryStream<Path> dir = Files.newDirectoryStream(staging, "*.tmp")) {
      for (Path p : dir) {
        UUID id = idOf(p, ".json.tmp");
        if ((id == null || !inFlight.contains(id)) && modifiedBefore(p, cutoff)) {
          Files.deleteIfExists(p);
          removed++;
        }
      }
    }
    return removed;
  }

  private static boolean modifiedBefore(Path p, Instant cutoff) throws IOException {
    return Files.getLastModifiedTime(p).toInstant().isBefore(cutoff);
  }

  private static UUID idOf(Path path, String suffix) {
    Path fileName = path.getFileName();
    if (fileName == null) {
      return null;
    }
    String name = fileName.toString();
    if (!name.endsWith(suffix)) {
      return null;
    }
    try {
      return UUID.fromString(name.substring(0, name.length() - suffix.length()));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static void fsyncDirectory(Path dir) throws IOException {
    try (FileChannel ch = FileChannel.open(dir, StandardOpenOption.READ)) {
      ch.force(true);
    } catch (IOException e) {
      // Some filesystems do not allow opening directories; the file itself is already synced.
      if (!Files.isDirectory(dir)) {
        throw e;
      }
    }
  }
}
