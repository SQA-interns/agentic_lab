package org.example.conference.export;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.example.conference.registration.service.RegistrationQuery;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Organizer-only Excel export (US-008, AR-06); protected by HTTP Basic in SecurityConfig. */
@RestController
public class ExportController {

  static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  private final RegistrationQuery query;
  private final ExcelExportWriter writer;
  private final Clock clock;

  public ExportController(RegistrationQuery query, ExcelExportWriter writer, Clock clock) {
    this.query = query;
    this.writer = writer;
    this.clock = clock;
  }

  @GetMapping("/api/organizer/registrations/export.xlsx")
  public ResponseEntity<byte[]> export() {
    byte[] workbook = writer.write(query.findAll());
    return ResponseEntity.ok()
        .contentType(XLSX)
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("registrations-" + STAMP.format(clock.instant()) + ".xlsx")
                .build()
                .toString())
        .body(workbook);
  }
}
