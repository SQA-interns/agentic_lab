package si.konferenca.registration.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.RegistrationCommand;
import si.konferenca.registration.application.RegistrationCopy;
import si.konferenca.registration.application.RegistrationService;

/** POST /api/registrations (US-001, US-002; 02_contracts/openapi.json createRegistration). */
@RestController
public class RegistrationController {

  private final RegistrationService service;

  public RegistrationController(RegistrationService service) {
    this.service = service;
  }

  /** The RegistrationRequest schema; unknown properties are rejected by the JSON binding. */
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
      List<String> consents,
      String recaptchaToken) {

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
          consents,
          recaptchaToken);
    }
  }

  /** The Registration schema returned after acceptance. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record RegistrationResponse(
      UUID id,
      String type,
      Instant submittedAt,
      String firstName,
      String lastName,
      String email,
      String organization,
      String studyInstitution,
      String studyProgramme,
      String studentId,
      List<RegistrationCopy.Option> options,
      List<RegistrationCopy.Consent> consents) {

    static RegistrationResponse of(RegistrationCopy c) {
      return new RegistrationResponse(
          c.id(),
          c.type(),
          c.submittedAt(),
          c.firstName(),
          c.lastName(),
          c.email(),
          c.organization(),
          c.studyInstitution(),
          c.studyProgramme(),
          c.studentId(),
          c.options(),
          c.consents());
    }
  }

  @PostMapping(path = "/api/registrations", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RegistrationResponse> register(
      @RequestBody RegistrationRequest body, HttpServletRequest request) {
    RegistrationService.Accepted accepted =
        service.register(body.toCommand(), request.getRemoteAddr());
    return ResponseEntity.status(HttpStatus.CREATED).body(RegistrationResponse.of(accepted.copy()));
  }
}
