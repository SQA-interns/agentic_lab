package org.conference.registration.api;

import jakarta.validation.Valid;
import org.conference.registration.api.dto.ApiResponses.RegistrationResponse;
import org.conference.registration.api.dto.ExternalRegistrationRequest;
import org.conference.registration.api.dto.StudentRegistrationRequest;
import org.conference.registration.service.RegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Registration submission endpoints (US-001, US-002). */
@RestController
@RequestMapping(path = "/api/registrations", consumes = MediaType.APPLICATION_JSON_VALUE)
public class RegistrationController {

  private final RegistrationService registrationService;

  public RegistrationController(RegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  @PostMapping("/external")
  @ResponseStatus(HttpStatus.CREATED)
  public RegistrationResponse registerExternal(
      @Valid @RequestBody ExternalRegistrationRequest request) {
    return RegistrationResponse.of(registrationService.register(request.toCommand()));
  }

  @PostMapping("/student")
  @ResponseStatus(HttpStatus.CREATED)
  public RegistrationResponse registerStudent(
      @Valid @RequestBody StudentRegistrationRequest request) {
    return RegistrationResponse.of(registrationService.register(request.toCommand()));
  }
}
