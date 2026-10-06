package si.konferenca.registration.web;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.RegistrationResult;
import si.konferenca.registration.application.RegistrationService;
import si.konferenca.registration.application.StorageFailedException;
import si.konferenca.registration.domain.ErrorCode;
import si.konferenca.registration.domain.FieldError;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationSubmission;

/** POST /api/registrations (US-001, US-002, US-004, docs/02_contracts/api.openapi.yaml). */
@RestController
public class RegistrationController {

  private static final Logger LOG = LoggerFactory.getLogger(RegistrationController.class);

  private final RegistrationService service;

  public RegistrationController(RegistrationService service) {
    this.service = service;
  }

  /** Request body; every property optional here so that validation can name each problem. */
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
      List<String> consents,
      String captchaToken) {

    RegistrationSubmission toSubmission() {
      return new RegistrationSubmission(
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
          captchaToken);
    }
  }

  record SelectedOption(String id, String name, String category) {}

  record Confirmation(
      String registrationId,
      String type,
      String firstName,
      String lastName,
      String email,
      List<SelectedOption> options,
      String receivedAt) {

    static Confirmation of(Registration r) {
      return new Confirmation(
          r.id().toString(),
          r.type().value(),
          r.participant().firstName(),
          r.participant().lastName(),
          r.participant().email(),
          r.options().stream()
              .map(o -> new SelectedOption(o.id(), o.name(), o.category().value()))
              .toList(),
          r.receivedAt().toString());
    }
  }

  @PostMapping(path = "/api/registrations", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> register(@RequestBody RegistrationRequest request) {
    RegistrationResult result = service.register(request.toSubmission());
    return switch (result) {
      case RegistrationResult.Accepted accepted -> {
        LOG.info("Registration {} accepted", accepted.registration().id());
        yield ResponseEntity.status(HttpStatus.CREATED)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Confirmation.of(accepted.registration()));
      }
      case RegistrationResult.Invalid invalid ->
          Problems.of(
              HttpStatus.BAD_REQUEST,
              "Registration rejected",
              "Some fields need to be corrected.",
              invalid.errors());
      case RegistrationResult.CaptchaRejected rejected ->
          Problems.of(
              HttpStatus.BAD_REQUEST,
              "Registration rejected",
              "The anti-automation check was not passed.",
              List.of(FieldError.of("captchaToken", ErrorCode.CAPTCHA_FAILED)));
      case RegistrationResult.CaptchaUnavailable unavailable ->
          Problems.of(
              HttpStatus.SERVICE_UNAVAILABLE,
              "Verification unavailable",
              "The anti-automation check could not be completed. Please try again later.");
      case RegistrationResult.DuplicateEmail duplicate ->
          Problems.of(
              HttpStatus.CONFLICT,
              "Already registered",
              ErrorCode.DUPLICATE_EMAIL.message(),
              List.of(FieldError.of("email", ErrorCode.DUPLICATE_EMAIL)));
    };
  }

  @ExceptionHandler(StorageFailedException.class)
  ResponseEntity<Problems.ProblemBody> storageFailed(StorageFailedException e) {
    LOG.error("Registration {} could not be stored", e.registrationId(), e.getCause());
    return Problems.of(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Registration could not be saved",
        "Your registration was not saved. Please try again later.");
  }
}
