package si.konferenca.registration.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.service.OptionCatalogService;
import si.konferenca.registration.service.RegistrationService;
import si.konferenca.registration.web.ApiDtos.ClientConfigDto;
import si.konferenca.registration.web.ApiDtos.OptionDto;
import si.konferenca.registration.web.ApiDtos.RegistrationRequest;
import si.konferenca.registration.web.ApiDtos.RegistrationResponse;

/** Public endpoints used by the registration forms (US-001 … US-004). */
@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class PublicController {

  private final OptionCatalogService catalog;
  private final RegistrationService registrations;
  private final ClientConfigDto clientConfig;

  public PublicController(
      OptionCatalogService catalog,
      RegistrationService registrations,
      @Value("${app.recaptcha.site-key:}") String siteKey,
      @Value("${app.recaptcha.test-mode:false}") boolean testMode) {
    this.catalog = catalog;
    this.registrations = registrations;
    this.clientConfig = new ClientConfigDto(siteKey == null ? "" : siteKey, testMode);
  }

  @GetMapping("/options")
  public List<OptionDto> activeOptions() {
    return catalog.activeOptions().stream().map(OptionDto::of).toList();
  }

  @GetMapping("/config")
  public ClientConfigDto clientConfig() {
    return clientConfig;
  }

  @PostMapping(path = "/registrations", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public RegistrationResponse register(
      @RequestBody RegistrationRequest request, HttpServletRequest http) {
    return RegistrationResponse.of(
        registrations.register(request.toCommand(), http.getRemoteAddr()));
  }
}
