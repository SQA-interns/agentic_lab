package si.konferenca.registration.domain;

/** Port: the anti-automation check of a registration (SR-01). */
public interface CaptchaVerifier {

  /**
   * Whether the token proves a passed check.
   *
   * @throws CaptchaUnavailableException when the verification service cannot be asked
   */
  boolean verify(String token);

  /** The verification service could not be reached or gave no usable answer. */
  class CaptchaUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CaptchaUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
