package si.konferenca.registration.application;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/** Port: raw JSON copies on persistent storage (BR-07). */
public interface JsonCopyStore {

  /**
   * Writes the copy completely or not at all.
   *
   * @return the written file, to be deleted if the database transaction fails afterwards
   * @throws java.io.UncheckedIOException when the copy cannot be written
   */
  Path write(UUID id, Instant acceptedAt, byte[] json);

  /** Removes a copy whose registration was not stored. */
  void delete(Path copy);
}
