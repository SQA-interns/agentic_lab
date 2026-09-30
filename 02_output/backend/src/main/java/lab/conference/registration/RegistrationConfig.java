package lab.conference.registration;

import java.io.IOException;
import java.nio.file.Path;
import lab.conference.options.Catalog;
import lab.conference.platform.AppProfile;
import lab.conference.platform.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registration module wiring and startup guards (SR-01, SR-02). */
@Configuration
public class RegistrationConfig {

  @Bean
  RegistrationValidator registrationValidator(Catalog catalog) {
    return new RegistrationValidator(catalog);
  }

  @Bean
  JsonStore jsonStore(AppProperties props) throws IOException {
    if (props.jsonDir() == null || props.jsonDir().isBlank()) {
      throw new IllegalStateException("REGISTRATION_JSON_DIR must be set");
    }
    return new JsonStore(Path.of(props.jsonDir()));
  }

  @Bean
  OrganizerRecipients organizerRecipients(AppProperties props) {
    return new OrganizerRecipients(props.mail() == null ? null : props.mail().organizerEmails());
  }

  /** The stub is refused in production; recaptcha requires both keys (fail closed). */
  @Bean
  CaptchaVerifier captchaVerifier(AppProperties props, AppProfile profile) {
    AppProperties.Captcha c = props.captcha();
    String mode = c == null || c.mode() == null ? "" : c.mode().trim();
    return switch (mode) {
      case "stub" -> stub(profile);
      case "recaptcha" -> recaptcha(c);
      default -> throw new IllegalStateException("CAPTCHA_MODE must be stub or recaptcha");
    };
  }

  static CaptchaVerifier stub(AppProfile profile) {
    if (!profile.allowsTestSubstitutes()) {
      throw new IllegalStateException("CAPTCHA_MODE=stub is not allowed in production");
    }
    return new StubCaptchaVerifier();
  }

  static CaptchaVerifier recaptcha(AppProperties.Captcha c) {
    if (isBlank(c.secretKey()) || isBlank(c.siteKey())) {
      throw new IllegalStateException(
          "CAPTCHA_MODE=recaptcha needs RECAPTCHA_SITE_KEY and RECAPTCHA_SECRET_KEY");
    }
    return new RecaptchaVerifier(c.siteKey().trim(), c.secretKey().trim());
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
