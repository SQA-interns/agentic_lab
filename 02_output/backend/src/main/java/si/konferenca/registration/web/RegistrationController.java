package si.konferenca.registration.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.MalformedRequestException;
import si.konferenca.registration.application.RegistrationService;
import si.konferenca.registration.domain.Registration;

/** POST /api/registrations (US-001, US-002, US-004). */
@RestController
public class RegistrationController {

  private final RegistrationService service;

  public RegistrationController(RegistrationService service) {
    this.service = service;
  }

  @PostMapping(
      path = "/api/registrations",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Map<String, Object>> register(
      @RequestBody RegistrationRequest request, HttpServletRequest http) {
    if (request == null) {
      throw new MalformedRequestException("empty body");
    }
    Registration r = service.register(request.toCommand(), http.getRemoteAddr());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("registrationId", r.id().toString(), "receivedAt", r.receivedAt().toString()));
  }
}
