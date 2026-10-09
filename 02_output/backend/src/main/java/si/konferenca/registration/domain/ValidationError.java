package si.konferenca.registration.domain;

/**
 * One problem with a submission. {@code field} is the API field name; {@code consentId} is set only
 * with {@link ErrorCode#CONSENT_REQUIRED}.
 */
public record ValidationError(String field, ErrorCode code, String consentId) {

  public static ValidationError of(String field, ErrorCode code) {
    return new ValidationError(field, code, null);
  }

  public static ValidationError missingConsent(String consentId) {
    return new ValidationError("consentIds", ErrorCode.CONSENT_REQUIRED, consentId);
  }
}
