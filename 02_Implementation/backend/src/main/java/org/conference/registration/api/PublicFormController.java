package org.conference.registration.api;

import java.util.List;
import org.conference.registration.api.dto.ApiResponses.FormTokenResponse;
import org.conference.registration.api.dto.ApiResponses.OptionResponse;
import org.conference.registration.domain.RegistrationType;
import org.conference.registration.service.FormTokenService;
import org.conference.registration.service.OptionCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Data the registration forms need before submission (US-003, anti-automation). */
@RestController
@RequestMapping("/api")
public class PublicFormController {

  private final OptionCatalog optionCatalog;
  private final FormTokenService formTokenService;

  public PublicFormController(OptionCatalog optionCatalog, FormTokenService formTokenService) {
    this.optionCatalog = optionCatalog;
    this.formTokenService = formTokenService;
  }

  @GetMapping("/options")
  public List<OptionResponse> options(@RequestParam("type") RegistrationType type) {
    return optionCatalog.activeFor(type).stream().map(OptionResponse::of).toList();
  }

  @GetMapping("/form-token")
  public FormTokenResponse formToken() {
    return new FormTokenResponse(formTokenService.issue());
  }
}
