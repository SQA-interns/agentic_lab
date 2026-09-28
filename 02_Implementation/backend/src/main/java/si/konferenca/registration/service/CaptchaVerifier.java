package si.konferenca.registration.service;

/** Port: verification of anti-automation (reCAPTCHA) tokens. */
public interface CaptchaVerifier {

  /** Returns {@code true} only if the token is valid. Never throws for invalid tokens. */
  boolean verify(String token, String remoteIp);

  /** Settings the frontend needs to render the matching captcha widget. */
  CaptchaSettings settings();

  /** Captcha mode and the public site key (only in {@link Mode#RECAPTCHA} mode). */
  record CaptchaSettings(Mode mode, String siteKey) {

    /** Captcha modes. */
    public enum Mode {
      TEST,
      RECAPTCHA
    }
  }
}
