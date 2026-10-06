package si.konferenca.registration.application;

/** One rejected request field with its contract error code (openapi.yaml FieldError). */
public record FieldError(String field, String code) {

  public static final String REQUIRED = "REQUIRED";
  public static final String INVALID_EMAIL = "INVALID_EMAIL";
  public static final String TOO_LONG = "TOO_LONG";
  public static final String INVALID_CHARACTERS = "INVALID_CHARACTERS";
  public static final String NOT_ALLOWED_FOR_TYPE = "NOT_ALLOWED_FOR_TYPE";
  public static final String UNKNOWN_OPTION = "UNKNOWN_OPTION";
  public static final String INACTIVE_OPTION = "INACTIVE_OPTION";
  public static final String OPTION_NOT_OFFERED = "OPTION_NOT_OFFERED";
  public static final String CONSENT_REQUIRED = "CONSENT_REQUIRED";
  public static final String RECAPTCHA_FAILED = "RECAPTCHA_FAILED";
}
