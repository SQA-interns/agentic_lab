package si.konferenca.registration.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.service.RegistrationCommand;
import si.konferenca.registration.service.RegistrationService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** {@code POST /api/registrations} (api.openapi.yaml). */
@RestController
@RequestMapping("/api")
public class RegistrationController {

  /** Strict reading: unknown properties and wrong JSON types are malformed. */
  private static final JsonMapper STRICT =
      JsonMapper.builder()
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .build();

  private final RegistrationService service;

  public RegistrationController(RegistrationService service) {
    this.service = service;
  }

  @PostMapping(path = "/registrations", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Accepted> register(@RequestBody byte[] body, HttpServletRequest request) {
    RegistrationRequest parsed = parse(body);
    Registration registration = service.register(parsed.toCommand(), request.getRemoteAddr());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new Accepted(registration.id().toString(), registration.receivedAt().toString()));
  }

  private static RegistrationRequest parse(byte[] body) {
    try {
      RegistrationRequest parsed = STRICT.readValue(body, RegistrationRequest.class);
      if (parsed == null) {
        throw new MalformedRequestException();
      }
      return parsed;
    } catch (JacksonException e) {
      throw new MalformedRequestException();
    }
  }

  /** Request body (RegistrationRequest). */
  record RegistrationRequest(
      String type,
      String firstName,
      String lastName,
      String email,
      String organization,
      String studyInstitution,
      String studyProgramme,
      String studentId,
      List<String> optionIds,
      List<String> consentIds,
      String recaptchaToken) {

    RegistrationRequest {
      optionIds =
          optionIds == null ? null : List.copyOf(optionIds.stream().map(String::valueOf).toList());
      consentIds =
          consentIds == null
              ? null
              : List.copyOf(consentIds.stream().map(String::valueOf).toList());
    }

    RegistrationCommand toCommand() {
      return new RegistrationCommand(
          type,
          firstName,
          lastName,
          email,
          organization,
          studyInstitution,
          studyProgramme,
          studentId,
          optionIds,
          consentIds,
          recaptchaToken);
    }
  }

  /** Response body (RegistrationAccepted). */
  public record Accepted(String id, String receivedAt) {}
}
