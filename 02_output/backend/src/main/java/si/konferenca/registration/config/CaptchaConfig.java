package si.konferenca.registration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import si.konferenca.registration.application.CaptchaVerifier;
import si.konferenca.registration.infrastructure.GoogleCaptchaVerifier;
import si.konferenca.registration.infrastructure.TestModeCaptchaVerifier;
import si.konferenca.registration.settings.AppProperties;

/** Chooses the reCAPTCHA verifier from configuration; test mode only when enabled (SR-02). */
@Configuration
public class CaptchaConfig {

  @Bean
  CaptchaVerifier captchaVerifier(AppProperties properties) {
    AppProperties.Recaptcha recaptcha = properties.recaptcha();
    if (recaptcha.testMode()) {
      return new TestModeCaptchaVerifier();
    }
    return new GoogleCaptchaVerifier(recaptcha.verifyUrl(), recaptcha.secretKey());
  }
}
