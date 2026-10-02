package si.konferenca.registration.adapter.in.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.ExportRegistrations;

/** The organizer-only export (openapi.yaml, exportRegistrations). Access is checked before. */
@RestController
public class ExportController {

  /** The path of the export, also used by the access rules. */
  public static final String PATH = "/api/registrations/export";

  private static final Logger LOG = LoggerFactory.getLogger(ExportController.class);
  private static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final ExportRegistrations exportRegistrations;

  public ExportController(ExportRegistrations exportRegistrations) {
    this.exportRegistrations = exportRegistrations;
  }

  @GetMapping(PATH)
  public ResponseEntity<byte[]> export() {
    byte[] workbook = exportRegistrations.export();
    LOG.info("registrations exported by the organizer");
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(XLSX);
    headers.setContentDisposition(ContentDisposition.attachment().filename("prijave.xlsx").build());
    headers.setCacheControl(CacheControl.noStore());
    return ResponseEntity.ok().headers(headers).body(workbook);
  }
}
