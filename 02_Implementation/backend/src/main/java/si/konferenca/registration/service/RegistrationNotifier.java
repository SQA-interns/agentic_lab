package si.konferenca.registration.service;

/** Port: email notifications for accepted registrations. */
public interface RegistrationNotifier {

  void sendParticipantConfirmation(RegistrationSnapshot registration);

  void sendOrganizerNotification(RegistrationSnapshot registration, byte[] registrationJson);
}
