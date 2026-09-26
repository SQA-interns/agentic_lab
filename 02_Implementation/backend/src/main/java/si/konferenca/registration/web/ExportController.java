package si.konferenca.registration.web;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.ExportService;

/** Organizer-only Excel export; access control is configured in {@code SecurityConfig}. */
@RestController
public class ExportController {

  static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private static final DateTimeFormatter FILE_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

  private final ExportService exportService;
  private final Clock clock;

  public ExportController(ExportService exportService, Clock clock) {
    this.exportService = exportService;
    this.clock = clock;
  }

  @GetMapping("/api/admin/registrations/export")
  public ResponseEntity<byte[]> export() {
    byte[] workbook = exportService.exportWorkbook();
    String filename = "registrations-" + FILE_TIMESTAMP.format(clock.instant()) + ".xlsx";
    return ResponseEntity.ok()
        .contentType(XLSX)
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename).build().toString())
        .body(workbook);
  }
}
