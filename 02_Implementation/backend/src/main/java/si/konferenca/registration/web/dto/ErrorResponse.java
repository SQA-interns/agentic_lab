package si.konferenca.registration.web.dto;

import java.util.List;
import si.konferenca.registration.application.FieldViolation;

/** Uniform error body for every API error. */
public record ErrorResponse(String code, String message, List<FieldViolation> fieldErrors) {

  public ErrorResponse {
    fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
  }

  public static ErrorResponse of(String code, String message) {
    return new ErrorResponse(code, message, List.of());
  }
}
