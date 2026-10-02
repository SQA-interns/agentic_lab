package si.konferenca.registration.domain;

/** Port: the emails about an accepted registration. A failure is a RuntimeException. */
public interface MailNotifier {

  /** The confirmation for the participant (US-006). */
  void sendParticipantConfirmation(Registration registration);

  /** The notification for every organizer recipient, with the raw JSON copy attached (US-007). */
  void sendOrganizerNotification(Registration registration, byte[] jsonCopy);
}
