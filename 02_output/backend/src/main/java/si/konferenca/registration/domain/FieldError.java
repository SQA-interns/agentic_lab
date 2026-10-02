package si.konferenca.registration.domain;

/** One reason why a registration is rejected: the field and an error code of the API contract. */
public record FieldError(String field, String code) {

  public static final String REQUIRED = "required";
  public static final String TOO_LONG = "too_long";
  public static final String INVALID_FORMAT = "invalid_format";
  public static final String INVALID_CHARACTERS = "invalid_characters";
  public static final String INVALID_TYPE = "invalid_type";
  public static final String OPTION_NOT_SELECTABLE = "option_not_selectable";
  public static final String OPTION_DUPLICATE = "option_duplicate";
  public static final String TOO_MANY = "too_many";
  public static final String CONSENT_REQUIRED = "consent_required";
  public static final String CAPTCHA_FAILED = "captcha_failed";
  public static final String UNKNOWN_FIELD = "unknown_field";
  public static final String MALFORMED = "malformed";

  public static final String FIELD_TYPE = "type";
  public static final String FIELD_OPTION_IDS = "optionIds";
  public static final String FIELD_CONSENT = "consent";
  public static final String FIELD_CAPTCHA_TOKEN = "captchaToken";
  public static final String FIELD_BODY = "body";
}
