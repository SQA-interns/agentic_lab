package si.konferenca.registration.application.port;

import java.io.IOException;
import java.util.List;
import si.konferenca.registration.domain.Registration;

/** Renders registrations as an Excel workbook. */
public interface RegistrationWorkbookWriter {

  byte[] write(List<Registration> registrations) throws IOException;
}
