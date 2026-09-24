package org.conference.registration.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Pattern;
import org.conference.registration.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Writes one JSON backup file per registration to persistent storage (US-005). */
@Service
public class BackupWriter {

  private static final Logger LOG = LoggerFactory.getLogger(BackupWriter.class);
  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
  private static final Pattern FILE_NAME =
      Pattern.compile(
          "^registration-\\d{8}T\\d{6}Z-[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.json$");

  private final Path directory;

  @Autowired
  public BackupWriter(AppProperties properties) {
    this(Path.of(properties.backup().dir()));
  }

  BackupWriter(Path directory) {
    this.directory = directory.toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.directory);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot create backup directory " + this.directory, e);
    }
    if (!Files.isWritable(this.directory)) {
      throw new IllegalStateException("Backup directory is not writable: " + this.directory);
    }
  }

  public static String fileNameFor(UUID id, Instant createdAt) {
    return "registration-" + STAMP.format(createdAt) + "-" + id + ".json";
  }

  /** Writes the file atomically (temp file, fsync, atomic rename). */
  public Path write(String fileName, byte[] content) {
    Path target = resolve(fileName);
    Path temp = target.resolveSibling(fileName + ".tmp");
    try {
      try (FileChannel channel =
          FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
        ByteBuffer buffer = ByteBuffer.wrap(content);
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
        channel.force(true);
      }
      restrictPermissions(temp);
      return Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      deleteQuietly(temp);
      throw new BackupFailedException("Could not write registration backup " + fileName, e);
    }
  }

  /** Removes a backup whose registration was rolled back. */
  public void delete(String fileName) {
    deleteQuietly(resolve(fileName));
  }

  private Path resolve(String fileName) {
    if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
      throw new IllegalArgumentException("Illegal backup file name");
    }
    return directory.resolve(fileName);
  }

  private static void restrictPermissions(Path file) throws IOException {
    if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
      Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    }
  }

  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      LOG.error("Could not delete backup file {}", file.getFileName(), e);
    }
  }
}
