package si.konferenca.registration.application;

/** Port: emails sent after a registration is stored (02_contracts/emails.md). */
public interface NotificationSender {

  /** Confirmation to the participant (US-006). */
  void sendParticipantConfirmation(RegistrationCopy registration);

  /** Notification to all organizers with the JSON copy attached unchanged (US-007). */
  void sendOrganizerNotification(RegistrationCopy registration, byte[] jsonCopy);
}
