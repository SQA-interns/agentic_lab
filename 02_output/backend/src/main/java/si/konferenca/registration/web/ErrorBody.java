package si.konferenca.registration.web;

import java.util.List;

/** The Error schema of docs/02_contracts/openapi.json. */
public record ErrorBody(
    int status, String error, String message, List<FieldErrorBody> fieldErrors) {

  /** One offending request field. */
  public record FieldErrorBody(String field, String message) {}

  public static ErrorBody of(int status, String error, String message) {
    return new ErrorBody(status, error, message, List.of());
  }
}
