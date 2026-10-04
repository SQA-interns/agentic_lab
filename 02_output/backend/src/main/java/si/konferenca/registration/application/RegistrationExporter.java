package si.konferenca.registration.application;

import java.util.List;
import si.konferenca.registration.domain.Registration;

/** Port: the export workbook of registration-api.openapi.yaml, exportRegistrations. */
public interface RegistrationExporter {

  byte[] export(List<Registration> registrations);
}
