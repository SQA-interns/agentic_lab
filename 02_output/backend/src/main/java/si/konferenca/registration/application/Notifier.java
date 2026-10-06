package si.konferenca.registration.application;

import si.konferenca.registration.domain.Registration;

/** Port: participant and organizer emails, sent after the registration is stored (D-14). */
public interface Notifier {

  /** Sends both emails without blocking the caller; never throws. */
  void registrationAccepted(Registration registration, byte[] jsonCopy);
}
