package si.konferenca.registration.application.port;

import si.konferenca.registration.domain.Registration;

/** Sends the participant confirmation and the organizer notification. */
public interface RegistrationNotifier {

  void registrationAccepted(Registration registration, String json);
}
