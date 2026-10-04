package si.konferenca.registration.application;

import si.konferenca.registration.domain.Registration;

/** Port: emails after a registration was stored (US-006, US-007). */
public interface RegistrationNotifier {

  /**
   * Sends the participant confirmation and one organizer notification per address, with the raw
   * JSON copy attached. A failed email is logged and never thrown (D-08).
   */
  void registrationAccepted(Registration registration, byte[] jsonCopy);
}
