package si.konferenca.registration.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.application.RegistrationService;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.web.dto.RegistrationRequest;
import si.konferenca.registration.web.dto.RegistrationResponse;

@RestController
public class RegistrationController {

  private final RegistrationService registrationService;

  public RegistrationController(RegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  @PostMapping(path = "/api/registrations", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public RegistrationResponse register(
      @RequestBody RegistrationRequest request, HttpServletRequest httpRequest) {
    Registration registration =
        registrationService.register(request.toCommand(), httpRequest.getRemoteAddr());
    return new RegistrationResponse(registration.getId(), registration.getType());
  }
}
