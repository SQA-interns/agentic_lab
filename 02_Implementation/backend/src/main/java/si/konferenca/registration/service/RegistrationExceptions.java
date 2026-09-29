package si.konferenca.registration.service;

import java.util.List;

/** Outcomes of a submission that are not an accepted registration. */
public final class RegistrationExceptions {

  private RegistrationExceptions() {}

  /** The submission failed validation; nothing was stored. */
  public static class ValidationFailedException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final transient List<FieldError> errors;

    public ValidationFailedException(List<FieldError> errors) {
      super("registration validation failed");
      this.errors = List.copyOf(errors);
    }

    public List<FieldError> errors() {
      return errors;
    }
  }

  /** The reCAPTCHA token was missing or rejected; nothing was stored. */
  public static class RecaptchaFailedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RecaptchaFailedException() {
      super("reCAPTCHA verification failed");
    }
  }

  /** The registration could not be stored in the database and backup; nothing was stored. */
  public static class RegistrationNotSavedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RegistrationNotSavedException(Throwable cause) {
      super("registration could not be stored", cause);
    }
  }
}
