package org.example.conference.captcha;

/** Startup failure for unsafe or incomplete captcha configuration (fail closed). */
public class CaptchaConfigurationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public CaptchaConfigurationException(String message) {
    super(message);
  }
}
