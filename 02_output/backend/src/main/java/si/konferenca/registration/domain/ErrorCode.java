package si.konferenca.registration.domain;

/** Field error codes of the API contract (api.openapi.yaml, FieldError). */
public enum ErrorCode {
  REQUIRED("This field is required."),
  INVALID_EMAIL("Enter a valid email address."),
  TOO_LONG("This value is too long."),
  INVALID_CHARACTERS("This value contains characters that are not allowed."),
  NOT_ALLOWED("This field is not part of this registration type."),
  UNKNOWN_OPTION("One of the selected options does not exist."),
  INACTIVE_OPTION("One of the selected options is no longer offered."),
  OPTION_NOT_OFFERED("One of the selected options is not offered for this registration type."),
  DUPLICATE_OPTION("An option was selected more than once."),
  CONSENT_REQUIRED("This consent is required."),
  UNKNOWN_CONSENT("One of the given consents does not exist."),
  CAPTCHA_FAILED("Please confirm that you are not a robot."),
  DUPLICATE_EMAIL("This email is already registered. Please contact the organizers."),
  MALFORMED("The request could not be read.");

  private final String message;

  ErrorCode(String message) {
    this.message = message;
  }

  /** Default message, without internal details (ES-07). */
  public String message() {
    return message;
  }
}
