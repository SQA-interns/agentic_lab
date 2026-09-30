package lab.conference.registration;

/** Deterministic local/test substitute: only {@link #VALID_TOKEN} passes. */
public final class StubCaptchaVerifier implements CaptchaVerifier {

  public static final String VALID_TOKEN = "local-captcha-ok";

  @Override
  public String mode() {
    return "stub";
  }

  @Override
  public String siteKey() {
    return null;
  }

  @Override
  public boolean verify(String token, String remoteAddress) {
    return VALID_TOKEN.equals(token);
  }
}
