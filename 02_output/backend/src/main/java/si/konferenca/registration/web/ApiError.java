package si.konferenca.registration.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** The `Error` body of `openapi.yaml`; messages carry no internal details (ES-07). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, List<FieldErrorBody> fieldErrors) {

  public ApiError {
    fieldErrors = fieldErrors == null ? null : List.copyOf(fieldErrors);
  }

  /** One field error. */
  public record FieldErrorBody(String field, String code, String message) {}

  public static ApiError of(String code, String message) {
    return new ApiError(code, message, null);
  }
}
