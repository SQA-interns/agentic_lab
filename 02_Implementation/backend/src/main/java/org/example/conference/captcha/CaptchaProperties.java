package org.example.conference.captcha;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Captcha settings. {@code mode} defaults to RECAPTCHA; TEST is honoured only with a {@code local}
 * or {@code test} profile (see {@link CaptchaConfiguration}).
 */
@ConfigurationProperties(prefix = "app.captcha")
public record CaptchaProperties(
    CaptchaMode mode, String siteKey, String secret, String verifyUrl, String testToken) {

  public CaptchaProperties {
    mode = mode == null ? CaptchaMode.RECAPTCHA : mode;
    verifyUrl =
        verifyUrl == null || verifyUrl.isBlank()
            ? "https://www.google.com/recaptcha/api/siteverify"
            : verifyUrl;
  }

  @Override
  public String toString() {
    return "CaptchaProperties[mode=" + mode + ", siteKey=" + siteKey + ", secret=<redacted>]";
  }
}
