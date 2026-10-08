package si.konferenca.registration.api;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.service.ExportService;

/** {@code GET /api/export}, organizer only (BR-08; access rules in SecurityConfig). */
@RestController
@RequestMapping("/api")
public class ExportController {

  static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final ExportService exportService;

  public ExportController(ExportService exportService) {
    this.exportService = exportService;
  }

  @GetMapping("/export")
  public ResponseEntity<byte[]> export() {
    return ResponseEntity.ok()
        .contentType(XLSX)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("registrations.xlsx").build().toString())
        .body(exportService.workbook());
  }
}
