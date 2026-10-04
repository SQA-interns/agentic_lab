package si.konferenca.registration.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.RegistrationService;
import tools.jackson.databind.JsonNode;

/** POST /api/registrations (registration-api.openapi.yaml, createRegistration). */
@RestController
public class RegistrationController {

  private final RegistrationService service;

  public RegistrationController(RegistrationService service) {
    this.service = service;
  }

  /** Response body of a 201. */
  public record RegistrationAccepted(UUID id, String type, Instant acceptedAt) {}

  @PostMapping(path = "/api/registrations", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RegistrationAccepted> register(
      @RequestBody JsonNode body, HttpServletRequest request) {
    RegistrationService.Accepted accepted =
        service.register(RegistrationRequestReader.read(body), request.getRemoteAddr());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            new RegistrationAccepted(accepted.id(), accepted.type().name(), accepted.acceptedAt()));
  }
}
