package si.konferenca.registration.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.RegisterParticipant;
import si.konferenca.registration.application.RegistrationResult;
import si.konferenca.registration.domain.Field;
import si.konferenca.registration.domain.Registration;

/** {@code POST /api/registrations} (registration-api.openapi.yaml). */
@RestController
public class RegistrationController {

  private static final Logger LOG = LoggerFactory.getLogger(RegistrationController.class);

  private final RegisterParticipant useCase;

  public RegistrationController(RegisterParticipant useCase) {
    this.useCase = useCase;
  }

  @PostMapping(path = "/api/registrations", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> register(
      @RequestBody String body, HttpServletRequest request) {
    RegistrationRequestParser.Parsed parsed = RegistrationRequestParser.parse(body);
    if (!parsed.errors().isEmpty()) {
      return Problems.validation(HttpStatus.BAD_REQUEST, parsed.errors());
    }
    RegistrationResult result =
        useCase.register(parsed.submission(), parsed.captchaToken(), request.getRemoteAddr());
    return switch (result) {
      case RegistrationResult.Accepted accepted -> {
        LOG.info("Registration {} stored", accepted.registration().id());
        yield ResponseEntity.status(HttpStatus.CREATED)
            .contentType(MediaType.APPLICATION_JSON)
            .body(view(accepted.registration()));
      }
      case RegistrationResult.Rejected rejected ->
          Problems.validation(
              rejected.duplicate() ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST,
              rejected.errors());
    };
  }

  private static Map<String, Object> view(Registration registration) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", registration.id().toString());
    body.put("type", registration.type().name());
    body.put("firstName", registration.value(Field.FIRST_NAME));
    body.put("lastName", registration.value(Field.LAST_NAME));
    body.put(
        "selectedOptions",
        registration.selectedOptions().stream()
            .map(
                o ->
                    Map.<String, Object>of(
                        "id", o.id(), "name", o.name(), "category", o.category().name()))
            .toList());
    return body;
  }
}
