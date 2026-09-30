package si.konferenca.registration.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.api.Dtos.ClientConfig;
import si.konferenca.registration.api.Dtos.ConsentDto;
import si.konferenca.registration.api.Dtos.OptionDto;
import si.konferenca.registration.api.Dtos.OptionsResponse;
import si.konferenca.registration.api.Dtos.RegistrationConfirmation;
import si.konferenca.registration.api.Dtos.RegistrationRequest;
import si.konferenca.registration.application.CaptchaVerifier;
import si.konferenca.registration.application.ConferenceSettings;
import si.konferenca.registration.application.NotificationService;
import si.konferenca.registration.application.RegistrationRejectedException;
import si.konferenca.registration.application.RegistrationService;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.FieldError;
import si.konferenca.registration.domain.OptionsCatalog;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

/** Public endpoints: client configuration, options and registration (US-001 to US-004). */
@RestController
@RequestMapping("/api")
class PublicController {

  private final ConferenceSettings settings;
  private final CaptchaVerifier captcha;
  private final OptionsCatalog catalog;
  private final RegistrationService registrations;
  private final NotificationService notifications;

  PublicController(
      ConferenceSettings settings,
      CaptchaVerifier captcha,
      OptionsCatalog catalog,
      RegistrationService registrations,
      NotificationService notifications) {
    this.settings = settings;
    this.captcha = captcha;
    this.catalog = catalog;
    this.registrations = registrations;
    this.notifications = notifications;
  }

  @GetMapping("/config")
  ClientConfig config() {
    return new ClientConfig(
        settings.conferenceName(), captcha.testMode(), settings.recaptchaSiteKey());
  }

  @GetMapping("/options")
  OptionsResponse options(@RequestParam(name = "type", required = false) String type) {
    RegistrationType parsed =
        RegistrationType.parse(type)
            .filter(t -> t.name().equals(type))
            .orElseThrow(
                () ->
                    new RegistrationRejectedException(
                        List.of(new FieldError("type", "Unknown registration type."))));
    Map<String, Integer> limits = new LinkedHashMap<>();
    for (Category category : Category.values()) {
      catalog.limit(category).ifPresent(limit -> limits.put(category.name(), limit));
    }
    return new OptionsResponse(
        parsed.name(),
        catalog.offeredTo(parsed).stream().map(OptionDto::of).toList(),
        catalog.consents().stream().map(ConsentDto::of).toList(),
        limits);
  }

  @PostMapping("/registrations")
  ResponseEntity<RegistrationConfirmation> register(@RequestBody RegistrationRequest request) {
    Registration registration = registrations.register(request.toSubmission());
    // The transaction has committed: the database row and the JSON copy exist (AR-05).
    notifications.notifyAccepted(registration.reference());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(RegistrationConfirmation.of(registration));
  }
}
