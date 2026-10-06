package si.konferenca.registration.domain;

/** One rejected field: the request property, the code and a message for people. */
public record FieldError(String field, ErrorCode code, String message) {

  public static FieldError of(String field, ErrorCode code) {
    return new FieldError(field, code, code.message());
  }
}
