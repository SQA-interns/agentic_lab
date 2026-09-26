package si.konferenca.registration.application.port;

import java.io.IOException;
import si.konferenca.registration.domain.Registration;

/** Stores the raw JSON representation of an accepted registration on persistent storage. */
public interface RegistrationBackup {

  void write(Registration registration, String json) throws IOException;
}
