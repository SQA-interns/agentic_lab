package si.konferenca.registration.adapter.captcha;

import si.konferenca.registration.application.CaptchaVerifier;

/** Deterministic test mode (SR-02): only the token {@value #TOKEN} passes; no network call. */
public class TestModeCaptchaVerifier implements CaptchaVerifier {

  public static final String TOKEN = "test-pass";

  @Override
  public boolean verify(String token, String remoteAddress) {
    return TOKEN.equals(token);
  }
}
