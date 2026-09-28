package org.example.conference.registration.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.example.conference.registration.domain.ParticipantType;
import org.example.conference.registration.service.RegistrationCommand;
import org.example.conference.registration.service.RegistrationResult;
import org.example.conference.registration.service.RegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public registration endpoints for the two fixed forms (US-001, US-002). */
@RestController
@RequestMapping(path = "/api/registrations", consumes = MediaType.APPLICATION_JSON_VALUE)
public class RegistrationController {

  private final RegistrationService service;

  public RegistrationController(RegistrationService service) {
    this.service = service;
  }

  @PostMapping("/external")
  public ResponseEntity<RegistrationResponse> external(
      @Valid @RequestBody ExternalRegistrationRequest request, HttpServletRequest http) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("firstName", request.firstName());
    fields.put("lastName", request.lastName());
    fields.put("email", request.email());
    fields.put("organization", request.organization());
    return respond(
        new RegistrationCommand(
            request.clientRequestId(),
            ParticipantType.EXTERNAL,
            fields,
            request.selections() == null ? SelectionsRequest.EMPTY : request.selections(),
            request.consents(),
            request.captchaToken()),
        http);
  }

  @PostMapping("/student")
  public ResponseEntity<RegistrationResponse> student(
      @Valid @RequestBody StudentRegistrationRequest request, HttpServletRequest http) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("firstName", request.firstName());
    fields.put("lastName", request.lastName());
    fields.put("email", request.email());
    fields.put("studyInstitution", request.studyInstitution());
    fields.put("studyProgramme", request.studyProgramme());
    fields.put("studentId", request.studentId());
    return respond(
        new RegistrationCommand(
            request.clientRequestId(),
            ParticipantType.STUDENT,
            fields,
            request.selections() == null ? SelectionsRequest.EMPTY : request.selections(),
            request.consents(),
            request.captchaToken()),
        http);
  }

  private ResponseEntity<RegistrationResponse> respond(
      RegistrationCommand command, HttpServletRequest http) {
    RegistrationResult result = service.register(command, http.getRemoteAddr());
    RegistrationResponse body =
        new RegistrationResponse(
            result.registrationId(),
            result.participantType().name(),
            result.submittedAt(),
            "PENDING",
            result.replayed());
    return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(body);
  }
}
