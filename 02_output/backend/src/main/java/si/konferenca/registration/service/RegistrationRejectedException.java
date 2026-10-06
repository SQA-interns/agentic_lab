package si.konferenca.registration.service;

import java.util.List;

/** A registration that is not accepted; nothing was stored. */
public class RegistrationRejectedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** One field error (`openapi.yaml` Error.fieldErrors). */
  public record FieldError(String field, FieldCode code) {}

  private final ErrorCode code;
  private final transient List<FieldError> fieldErrors;

  public RegistrationRejectedException(ErrorCode code) {
    this(code, List.of());
  }

  public RegistrationRejectedException(ErrorCode code, List<FieldError> fieldErrors) {
    super(code.name());
    this.code = code;
    this.fieldErrors = List.copyOf(fieldErrors);
  }

  public ErrorCode code() {
    return code;
  }

  public List<FieldError> fieldErrors() {
    return fieldErrors;
  }
}
