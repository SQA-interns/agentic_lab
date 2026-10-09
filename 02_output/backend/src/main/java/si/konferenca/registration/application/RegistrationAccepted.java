package si.konferenca.registration.application;

import si.konferenca.registration.domain.Registration;

/**
 * Event published inside the storage transaction; listeners act after commit (US-006, US-007).
 *
 * @param registration the stored registration
 * @param jsonCopy the exact bytes of the JSON copy
 */
public record RegistrationAccepted(Registration registration, byte[] jsonCopy) {

  public RegistrationAccepted {
    jsonCopy = jsonCopy.clone();
  }

  @Override
  public byte[] jsonCopy() {
    return jsonCopy.clone();
  }
}
