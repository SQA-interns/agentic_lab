package si.konferenca.registration.service;

/** Error codes of `openapi.yaml` raised by the registration flow, with status and message. */
public enum ErrorCode {
  VALIDATION_FAILED(400, "Some fields are not valid."),
  MALFORMED_REQUEST(400, "The request could not be read."),
  CAPTCHA_FAILED(400, "The anti-automation check failed. Please try again."),
  CAPTCHA_UNAVAILABLE(503, "The anti-automation check is unavailable. Please try again later."),
  DUPLICATE_EMAIL(
      409,
      "This email address is already registered. Please contact the organizers if you need to"
          + " change your registration."),
  PAYLOAD_TOO_LARGE(413, "The request is too large."),
  STORAGE_FAILED(503, "Your registration could not be saved. Please try again later.");

  private final int status;
  private final String message;

  ErrorCode(int status, String message) {
    this.status = status;
    this.message = message;
  }

  public int status() {
    return status;
  }

  public String message() {
    return message;
  }
}
