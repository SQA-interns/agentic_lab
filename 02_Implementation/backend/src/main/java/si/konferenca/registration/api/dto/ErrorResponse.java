package si.konferenca.registration.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Uniform API error body. Never contains exception details or submitted values. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(String error, String message, List<FieldError> fieldErrors) {

  public ErrorResponse(String error, String message) {
    this(error, message, List.of());
  }

  /** Error of a single request field. */
  public record FieldError(String field, String code, String message) {}
}
