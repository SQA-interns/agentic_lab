package si.konferenca.registration.service;

/** One failing field of a submission (contract schema {@code FieldError}). */
public record FieldError(String field, Code code) {

  /** Field error codes from docs/contracts/openapi.yaml. */
  public enum Code {
    REQUIRED("This field is required."),
    INVALID_EMAIL("Enter a valid email address."),
    TOO_LONG("This value is too long."),
    INVALID_CHARACTERS("This value contains characters that are not allowed."),
    CONSENT_REQUIRED("You must agree to the processing of your personal data."),
    UNKNOWN_OPTION("This option does not exist."),
    INACTIVE_OPTION("This option is no longer available."),
    TOO_MANY_OPTIONS("Too many options selected."),
    FIELD_NOT_ALLOWED("This field does not apply to the selected registration type."),
    INVALID_VALUE("This value is not allowed."),
    RECAPTCHA_FAILED("Please confirm you are not a robot.");

    private final String message;

    Code(String message) {
      this.message = message;
    }

    public String message() {
      return message;
    }
  }
}
