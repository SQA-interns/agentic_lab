package org.example.conference.captcha;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

/**
 * Selects the captcha verifier and enforces isolation of the deterministic mode (ST-05, AC-X-01):
 * TEST mode requires an active {@code local} or {@code test} profile and never a {@code prod}
 * profile; RECAPTCHA mode requires both site key and secret.
 */
@Configuration(proxyBeanMethods = false)
public class CaptchaConfiguration {

  static final Set<String> NON_PRODUCTION_PROFILES = Set.of("local", "test");

  @Bean
  public CaptchaVerifier captchaVerifier(
      CaptchaProperties properties, Environment environment, RestClient.Builder builder) {
    Set<String> active = Set.of(environment.getActiveProfiles());
    boolean nonProduction = active.stream().anyMatch(NON_PRODUCTION_PROFILES::contains);
    if (active.contains("prod") && nonProduction) {
      throw new CaptchaConfigurationException("Profile 'prod' cannot be combined with local/test");
    }
    if (properties.mode() == CaptchaMode.TEST) {
      if (!nonProduction || active.contains("prod")) {
        throw new CaptchaConfigurationException(
            "Deterministic captcha mode requires a non-production (local/test) profile");
      }
      if (isBlank(properties.testToken())) {
        throw new CaptchaConfigurationException("app.captcha.test-token must be set in test mode");
      }
      return new DeterministicCaptchaVerifier(properties.testToken());
    }
    if (isBlank(properties.secret()) || isBlank(properties.siteKey())) {
      throw new CaptchaConfigurationException(
          "reCAPTCHA site key and secret are required (APP_CAPTCHA_SITE_KEY/APP_CAPTCHA_SECRET)");
    }
    RestClient client =
        builder
            .requestFactory(
                ClientHttpRequestFactoryBuilder.detect()
                    .build(
                        ClientHttpRequestFactorySettings.defaults()
                            .withConnectTimeout(Duration.ofSeconds(5))
                            .withReadTimeout(Duration.ofSeconds(5))))
            .build();
    return new RecaptchaVerifier(client, properties.verifyUrl(), properties.secret());
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
