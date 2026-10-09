package si.konferenca.registration.application;

import si.konferenca.registration.domain.Registration;

/** Emails about an accepted registration (port; US-006, US-007, docs/02_contracts/emails.json). */
public interface RegistrationNotifier {

  /** Confirmation to the participant. */
  void notifyParticipant(Registration registration);

  /** Notification to the organizers with the JSON copy attached byte for byte. */
  void notifyOrganizers(Registration registration, byte[] jsonCopy);
}
