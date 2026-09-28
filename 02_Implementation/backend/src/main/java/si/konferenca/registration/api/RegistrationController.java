package si.konferenca.registration.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.api.dto.ExternalRegistrationRequest;
import si.konferenca.registration.api.dto.RegistrationResponse;
import si.konferenca.registration.api.dto.StudentRegistrationRequest;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.service.NewRegistration;
import si.konferenca.registration.service.RegistrationResult;
import si.konferenca.registration.service.RegistrationService;

/** Public registration endpoints for external participants and students. */
@RestController
@RequestMapping(path = "/api/registrations", produces = MediaType.APPLICATION_JSON_VALUE)
public class RegistrationController {

  private final RegistrationService registrationService;

  public RegistrationController(RegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  @PostMapping(path = "/external", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public RegistrationResponse registerExternal(
      @Valid @RequestBody ExternalRegistrationRequest request, HttpServletRequest http) {
    NewRegistration command =
        new NewRegistration(
            RegistrationType.EXTERNAL,
            request.firstName(),
            request.lastName(),
            request.email(),
            request.organization(),
            null,
            null,
            null,
            request.optionIds(),
            request.consents(),
            request.captchaToken(),
            http.getRemoteAddr());
    return toResponse(registrationService.register(command));
  }

  @PostMapping(path = "/student", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public RegistrationResponse registerStudent(
      @Valid @RequestBody StudentRegistrationRequest request, HttpServletRequest http) {
    NewRegistration command =
        new NewRegistration(
            RegistrationType.STUDENT,
            request.firstName(),
            request.lastName(),
            request.email(),
            null,
            request.studyInstitution(),
            request.studyProgramme(),
            request.studentId(),
            request.optionIds(),
            request.consents(),
            request.captchaToken(),
            http.getRemoteAddr());
    return toResponse(registrationService.register(command));
  }

  private static RegistrationResponse toResponse(RegistrationResult result) {
    return new RegistrationResponse(result.registrationId(), result.type(), result.createdAt());
  }
}
