package si.konferenca.registration.api;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.ExportService;
import si.konferenca.registration.application.OrganizerAccessService;
import tools.jackson.databind.JsonNode;

/** Organizer token and export (createOrganizerToken, exportRegistrations). */
@RestController
public class OrganizerController {

  static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final OrganizerAccessService access;
  private final ExportService export;

  public OrganizerController(OrganizerAccessService access, ExportService export) {
    this.access = access;
    this.export = export;
  }

  /** Response body of createOrganizerToken. */
  public record TokenResponse(String token, Instant expiresAt) {}

  @PostMapping(path = "/api/organizer/token", consumes = MediaType.APPLICATION_JSON_VALUE)
  public TokenResponse token(@RequestBody JsonNode body, HttpServletRequest request) {
    requireHttps(request);
    if (body == null
        || !body.isObject()
        || body.size() != 2
        || !body.path("username").isString()
        || !body.path("password").isString()) {
      throw new MalformedRequestException();
    }
    return access
        .issue(body.get("username").asString(), body.get("password").asString())
        .map(t -> new TokenResponse(t.value(), t.expiresAt()))
        .orElseThrow(UnauthorizedException::new);
  }

  @GetMapping("/api/organizer/registrations/export")
  public ResponseEntity<byte[]> export(HttpServletRequest request) {
    requireHttps(request);
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    String token =
        authorization != null && authorization.startsWith("Bearer ")
            ? authorization.substring("Bearer ".length()).strip()
            : null;
    if (!access.isValid(token)) {
      throw new UnauthorizedException();
    }
    return ResponseEntity.ok()
        .contentType(XLSX)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("registrations.xlsx").build().toString())
        .body(export.exportAll());
  }

  /** SR-06: organizer credentials only over HTTPS or from the local machine. */
  private void requireHttps(HttpServletRequest request) {
    if (access.httpsOnly() && !request.isSecure() && !isLoopback(request.getRemoteAddr())) {
      throw new HttpsRequiredException();
    }
  }

  /**
   * Loopback check; the servlet remote address is always an IP literal, which {@link
   * InetAddress#getByName} parses without a name lookup.
   */
  static boolean isLoopback(String address) {
    if (address == null || address.isBlank()) {
      return false;
    }
    try {
      return InetAddress.getByName(address).isLoopbackAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }
}
