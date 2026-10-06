package si.konferenca.registration.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.ExportService;

/** GET /api/registrations/export, organizer only (US-008, BR-08; access in SecurityConfig). */
@RestController
public class ExportController {

  private static final Logger LOG = LoggerFactory.getLogger(ExportController.class);
  static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final ExportService exports;

  public ExportController(ExportService exports) {
    this.exports = exports;
  }

  @GetMapping("/api/registrations/export")
  public ResponseEntity<byte[]> export() {
    byte[] workbook = exports.export();
    LOG.info("Registrations exported by an organizer");
    return ResponseEntity.ok()
        .contentType(XLSX)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("registrations.xlsx").build().toString())
        .body(workbook);
  }
}
