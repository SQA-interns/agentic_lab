package si.konferenca.registration.service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import si.konferenca.registration.domain.Registration;

/** Port: renders registrations as an Excel workbook. */
public interface RegistrationExportWriter {

  void write(List<Registration> registrations, OutputStream out) throws IOException;
}
