package si.konferenca.registration.service;

/** Field error codes of `openapi.yaml` with their user-facing message. */
public enum FieldCode {
  REQUIRED("This field is required."),
  INVALID_FORMAT("Enter a valid email address."),
  TOO_LONG("This value is too long."),
  CONTROL_CHARACTER("This value contains characters that are not allowed."),
  NOT_ALLOWED("This field is not allowed for the selected registration type."),
  UNKNOWN_OPTION("An option you selected does not exist."),
  INACTIVE_OPTION("An option you selected is no longer offered."),
  OPTION_NOT_AVAILABLE("An option you selected is not available for your registration type."),
  DUPLICATE_OPTION("An option was selected more than once."),
  CATEGORY_LIMIT("Too many options selected in this group."),
  CONSENT_MISSING("This consent is required.");

  private final String message;

  FieldCode(String message) {
    this.message = message;
  }

  public String message() {
    return message;
  }
}
