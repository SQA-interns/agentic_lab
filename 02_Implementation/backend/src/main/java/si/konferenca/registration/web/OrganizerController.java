package si.konferenca.registration.web;

import java.io.IOException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.service.OrganizerService;
import si.konferenca.registration.service.OrganizerService.RestoreResult;
import si.konferenca.registration.web.ApiDtos.RestoreResultDto;

/** Organizer-only endpoints (US-005 restore, US-008 export); access control in SecurityConfig. */
@RestController
@RequestMapping("/api/organizer")
public class OrganizerController {

  static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private static final DateTimeFormatter FILE_STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

  private final OrganizerService organizer;
  private final Clock clock;

  public OrganizerController(OrganizerService organizer, Clock clock) {
    this.organizer = organizer;
    this.clock = clock;
  }

  @GetMapping("/registrations/export")
  public ResponseEntity<byte[]> export() throws IOException {
    byte[] workbook = organizer.exportWorkbook();
    String filename = "registrations-" + FILE_STAMP.format(clock.instant()) + ".xlsx";
    return ResponseEntity.ok()
        .contentType(XLSX)
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename).build().toString())
        .body(workbook);
  }

  @PostMapping(
      path = "/backups/restore",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public RestoreResultDto restore(@RequestBody Map<String, Object> body) throws IOException {
    if (!body.isEmpty()) {
      throw new ApiExceptionHandler.MalformedRequestException("restore takes an empty JSON object");
    }
    RestoreResult result = organizer.restoreFromBackups();
    return new RestoreResultDto(result.restored(), result.alreadyPresent(), result.failed());
  }
}
