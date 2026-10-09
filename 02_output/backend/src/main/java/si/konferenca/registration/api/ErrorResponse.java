package si.konferenca.registration.api;

import java.util.List;

/** Error body of every failed request (openapi.yaml, ErrorResponse). */
public record ErrorResponse(String error, String message, List<FieldErrorResponse> fieldErrors) {

  public ErrorResponse {
    fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
  }

  public static ErrorResponse of(String error, String message) {
    return new ErrorResponse(error, message, List.of());
  }

  /** One field error (openapi.yaml, FieldError). */
  public record FieldErrorResponse(String field, String code, String message) {}
}
