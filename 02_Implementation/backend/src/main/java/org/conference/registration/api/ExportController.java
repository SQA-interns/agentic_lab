package org.conference.registration.api;

import java.security.Principal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.conference.registration.service.ExcelExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Organizer-only Excel export (US-008). Authentication is enforced by the security config. */
@RestController
public class ExportController {

  static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
  private static final Logger LOG = LoggerFactory.getLogger(ExportController.class);
  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

  private final ExcelExportService exportService;
  private final Clock clock;

  public ExportController(ExcelExportService exportService, Clock clock) {
    this.exportService = exportService;
    this.clock = clock;
  }

  @GetMapping("/api/admin/registrations/export")
  public ResponseEntity<byte[]> export(Principal principal) {
    byte[] workbook = exportService.exportRegistrations();
    LOG.info("Registration export downloaded by organizer '{}'", principal.getName());
    String fileName = "registrations-" + STAMP.format(clock.instant()) + ".xlsx";
    return ResponseEntity.ok()
        .contentType(XLSX)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(fileName).build().toString())
        .body(workbook);
  }
}
