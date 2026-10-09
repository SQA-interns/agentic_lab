package si.konferenca.registration.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.RegistrationCommand;
import si.konferenca.registration.application.RegistrationService;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

/** POST /api/registrations (openapi.yaml, createRegistration). */
@RestController
public class RegistrationController {

  private static final int MAX_OPTION_IDS = 50;
  private static final int MAX_CONSENT_IDS = 20;

  private final RegistrationService registrationService;

  public RegistrationController(RegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  /** Request body (openapi.yaml, RegistrationRequest); unknown properties are rejected. */
  @JsonIgnoreProperties(ignoreUnknown = false)
  public record RegistrationRequest(
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
      String captchaToken) {
    public RegistrationRequest {
      optionIds = optionIds == null ? null : List.copyOf(optionIds);
      consentIds = consentIds == null ? null : List.copyOf(consentIds);
    }
  }

  /** 201 body (openapi.yaml, RegistrationCreated). */
  public record RegistrationCreated(String registrationId, String registeredAt, String type) {}

  /** The body is syntactically valid JSON but not a registration request. */
  static final class InvalidRequestException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    InvalidRequestException(String message) {
      super(message);
    }
  }

  @PostMapping(path = "/api/registrations", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public RegistrationCreated register(
      @RequestBody RegistrationRequest body, HttpServletRequest request) {
    Registration registration =
        registrationService.register(toCommand(body), request.getRemoteAddr());
    return new RegistrationCreated(
        registration.id().toString(),
        registration.registeredAt().toString(),
        registration.type().name());
  }

  private static RegistrationCommand toCommand(RegistrationRequest body) {
    if (body == null) {
      throw new InvalidRequestException("Request body is missing.");
    }
    RegistrationType type = type(body.type());
    if ((body.optionIds() != null && body.optionIds().size() > MAX_OPTION_IDS)
        || (body.consentIds() != null && body.consentIds().size() > MAX_CONSENT_IDS)) {
      throw new InvalidRequestException("Too many selections.");
    }
    return new RegistrationCommand(
        type,
        body.firstName(),
        body.lastName(),
        body.email(),
        body.organization(),
        body.studyInstitution(),
        body.studyProgramme(),
        body.studentId(),
        body.optionIds(),
        body.consentIds(),
        body.captchaToken());
  }

  private static RegistrationType type(String value) {
    if ("EXTERNAL".equals(value)) {
      return RegistrationType.EXTERNAL;
    }
    if ("STUDENT".equals(value)) {
      return RegistrationType.STUDENT;
    }
    throw new InvalidRequestException("Registration type must be EXTERNAL or STUDENT.");
  }
}
