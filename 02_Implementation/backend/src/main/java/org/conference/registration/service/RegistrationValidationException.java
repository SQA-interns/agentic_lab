package org.conference.registration.service;

/** A business-rule violation tied to one request field (e.g. an inactive option). */
public class RegistrationValidationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final String field;

  public RegistrationValidationException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String getField() {
    return field;
  }
}
