package si.konferenca.registration.application;

/** Anti-automation verification on the trusted side (port; SR-01). */
public interface CaptchaVerifier {

  /**
   * Accepts the token or throws.
   *
   * @throws CaptchaFailedException when the token is missing or rejected
   * @throws CaptchaUnavailableException when the verification service cannot answer
   */
  void verify(String token, String clientIp);

  /** The token is missing or was rejected. */
  final class CaptchaFailedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CaptchaFailedException() {
      super("Anti-automation token rejected");
    }
  }

  /** The verification service could not be reached or answered unexpectedly. */
  final class CaptchaUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CaptchaUnavailableException(Throwable cause) {
      super("Anti-automation verification unavailable", cause);
    }
  }
}
