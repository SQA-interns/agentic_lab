package si.konferenca.registration.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import si.konferenca.registration.service.ExportService;

/** Organizer-only Excel export of the current registrations (access control in security). */
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

  @GetMapping("/api/organizer/registrations.xlsx")
  public ResponseEntity<byte[]> export() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    exportService.exportAll(out);
    String fileName = "registrations-" + FILE_TIMESTAMP.format(clock.instant()) + ".xlsx";
    return ResponseEntity.ok()
        .contentType(XLSX)
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(fileName).build().toString())
        .body(out.toByteArray());
  }
}
