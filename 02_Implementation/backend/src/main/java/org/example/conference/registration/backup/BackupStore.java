package org.example.conference.registration.backup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Raw JSON files on the persistent volume. Layout: {@code staging/<id>.json.tmp} (written and
 * fsynced), atomically moved to {@code registrations/<id>.json}; {@code orphaned/} holds
 * quarantined files that were never accepted or were superseded.
 */
@Component
public final class BackupStore {

  private static final Logger LOG = LoggerFactory.getLogger(BackupStore.class);
  static final String TMP_SUFFIX = ".json.tmp";
  static final String JSON_SUFFIX = ".json";

  private final Path staging;
  private final Path registrations;
  private final Path orphaned;
  private final Clock clock;

  public BackupStore(BackupProperties properties, Clock clock) {
    Path root = Path.of(properties.directory());
    this.staging = root.resolve("staging");
    this.registrations = root.resolve("registrations");
    this.orphaned = root.resolve("orphaned");
    this.clock = clock;
    try {
      Files.createDirectories(staging);
      Files.createDirectories(registrations);
      Files.createDirectories(orphaned);
    } catch (IOException e) {
      throw new UncheckedIOException("Backup directory not usable: " + root, e);
    }
  }

  /** Stages, fsyncs and atomically publishes the JSON. On failure nothing stays published. */
  public void publish(UUID id, String json) throws IOException {
    Path tmp = staging.resolve(id + TMP_SUFFIX);
    Path target = file(id);
    boolean published = false;
    try {
      try (FileChannel channel =
          FileChannel.open(tmp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
        channel.write(java.nio.ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)));
        channel.force(true);
      }
      moveAtomically(tmp, target);
      published = true;
      fsyncDirectory(registrations);
    } finally {
      if (!published) {
        Files.deleteIfExists(tmp);
      }
    }
  }

  /** Compensation after a failed DB commit: remove, or quarantine if removal fails. */
  public void discard(UUID id) {
    Path target = file(id);
    try {
      Files.deleteIfExists(target);
    } catch (IOException e) {
      LOG.error("Cannot delete unaccepted backup {}; quarantining", id);
      quarantine(target, id + ".unaccepted" + JSON_SUFFIX);
    }
  }

  public Optional<String> read(UUID id) throws IOException {
    Path target = file(id);
    if (!Files.exists(target)) {
      return Optional.empty();
    }
    return Optional.of(Files.readString(target, StandardCharsets.UTF_8));
  }

  /** Moves a file into {@code orphaned/} under the given name (timestamp appended if taken). */
  public void quarantine(Path source, String name) {
    Path destination = orphaned.resolve(name);
    if (Files.exists(destination)) {
      destination = orphaned.resolve(clock.millis() + "-" + name);
    }
    try {
      moveAtomically(source, destination);
    } catch (IOException e) {
      LOG.error("Quarantine of {} failed: {}", name, e.getClass().getSimpleName());
    }
  }

  public boolean isWritable() {
    return Files.isDirectory(staging)
        && Files.isWritable(staging)
        && Files.isDirectory(registrations)
        && Files.isWritable(registrations);
  }

  public Path file(UUID id) {
    return registrations.resolve(id + JSON_SUFFIX);
  }

  Path stagingDirectory() {
    return staging;
  }

  Path registrationsDirectory() {
    return registrations;
  }

  private static void moveAtomically(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      throw new IOException("Atomic move not supported for backup directory", e);
    }
  }

  private static void fsyncDirectory(Path directory) throws IOException {
    try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
      channel.force(true);
    }
  }
}
