package org.example.conference.captcha;

/** Server-side verification of an anti-automation token (ST-05). */
public interface CaptchaVerifier {

  /**
   * Returns true only when the token is positively verified. Any error fails closed.
   *
   * @param token client token, may be null
   * @param remoteIp resolved client address, may be null
   */
  boolean verify(String token, String remoteIp);

  /** Mode exposed to the frontend: {@code recaptcha} or {@code test}. */
  CaptchaMode mode();
}
