package si.konferenca.registration.domain;

/** Validation error codes of the API contract. */
public enum ErrorCode {
  REQUIRED,
  INVALID_EMAIL,
  TOO_LONG,
  OPTION_NOT_AVAILABLE,
  TOO_MANY_OPTIONS,
  CONSENT_REQUIRED,
  UNKNOWN_CONSENT,
  ALREADY_REGISTERED,
  CAPTCHA_FAILED,
  MALFORMED
}
