package org.example.conference.registration.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.example.conference.captcha.CaptchaMode;
import org.example.conference.captcha.CaptchaProperties;
import org.example.conference.captcha.CaptchaVerifier;
import org.example.conference.catalog.Catalog;
import org.example.conference.catalog.OptionGroup;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the startup catalog and captcha mode to the frontend (US-003). */
@RestController
@RequestMapping("/api")
public class FormConfigController {

  private final FormConfigResponse response;

  public FormConfigController(
      Catalog catalog, CaptchaVerifier captchaVerifier, CaptchaProperties captchaProperties) {
    Map<String, List<FormConfigResponse.Option>> groups = new LinkedHashMap<>();
    for (OptionGroup group : OptionGroup.values()) {
      groups.put(
          group.key(),
          catalog.activeOptions(group).stream()
              .map(o -> new FormConfigResponse.Option(o.id(), o.name()))
              .toList());
    }
    List<FormConfigResponse.Consent> consents =
        catalog.consents().stream()
            .map(c -> new FormConfigResponse.Consent(c.id(), c.text(), c.required()))
            .toList();
    boolean testMode = captchaVerifier.mode() == CaptchaMode.TEST;
    FormConfigResponse.Captcha captcha =
        new FormConfigResponse.Captcha(
            captchaVerifier.mode().name().toLowerCase(Locale.ROOT),
            testMode ? null : captchaProperties.siteKey(),
            testMode ? captchaProperties.testToken() : null);
    this.response = new FormConfigResponse(catalog.conferenceName(), groups, consents, captcha);
  }

  @GetMapping("/form-config")
  public FormConfigResponse formConfig() {
    return response;
  }
}
