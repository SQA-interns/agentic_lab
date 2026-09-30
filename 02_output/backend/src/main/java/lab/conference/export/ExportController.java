package lab.conference.export;

import java.io.IOException;
import lab.conference.registration.RegistrationQueries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/organizer/export.xlsx; access is restricted to the organizer by SecurityConfig. */
@RestController
public class ExportController {

  static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
  private static final Logger LOG = LoggerFactory.getLogger(ExportController.class);
  private final RegistrationQueries queries;

  public ExportController(RegistrationQueries queries) {
    this.queries = queries;
  }

  @GetMapping("/api/organizer/export.xlsx")
  public ResponseEntity<byte[]> export() throws IOException {
    var registrations = queries.allAccepted();
    byte[] workbook = WorkbookWriter.write(registrations);
    LOG.info("Organizer export generated with {} registration(s)", registrations.size());
    return ResponseEntity.ok()
        .contentType(XLSX)
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("registrations.xlsx").build().toString())
        .body(workbook);
  }
}
