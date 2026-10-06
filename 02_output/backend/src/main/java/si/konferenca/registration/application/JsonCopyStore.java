package si.konferenca.registration.application;

import si.konferenca.registration.domain.Registration;

/** Port: raw JSON copies on persistent storage (json-copy.schema.json). */
public interface JsonCopyStore {

  /** The file name the copy of this registration gets. */
  String fileNameFor(Registration registration);

  /** Serialises the registration as its JSON copy. */
  byte[] serialize(Registration registration);

  /**
   * Durably writes the bytes under the registration's file name.
   *
   * @throws java.io.UncheckedIOException if the copy cannot be written
   */
  void write(Registration registration, byte[] json);

  /** Removes a copy written for a registration that was finally not accepted. */
  void delete(Registration registration);
}
