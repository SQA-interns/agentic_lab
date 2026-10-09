package si.konferenca.registration.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.ExportRegistrations;

/** {@code GET /api/registrations/export}, organizer only (BR-08). */
@RestController
public class ExportController {

  static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final ExportRegistrations useCase;

  public ExportController(ExportRegistrations useCase) {
    this.useCase = useCase;
  }

  @GetMapping("/api/registrations/export")
  public ResponseEntity<byte[]> export() {
    return ResponseEntity.ok()
        .contentType(XLSX)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"registrations.xlsx\"")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(useCase.export());
  }
}
