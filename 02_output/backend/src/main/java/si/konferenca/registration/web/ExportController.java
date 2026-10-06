package si.konferenca.registration.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.ExportService;

/** GET /api/admin/registrations/export, organizers only (US-008, BR-08). */
@RestController
public class ExportController {

  static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final ExportService export;

  public ExportController(ExportService export) {
    this.export = export;
  }

  @GetMapping("/api/admin/registrations/export")
  ResponseEntity<byte[]> export() {
    return ResponseEntity.ok()
        .contentType(XLSX)
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("registrations.xlsx").build().toString())
        .body(export.export());
  }
}
