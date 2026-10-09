package si.konferenca.registration.application;

/** Port: anti-automation verification (SR-01). */
public interface CaptchaVerifier {

  /** True only if the token proves a human interaction; errors and timeouts return false. */
  boolean verify(String token, String remoteAddress);
}
