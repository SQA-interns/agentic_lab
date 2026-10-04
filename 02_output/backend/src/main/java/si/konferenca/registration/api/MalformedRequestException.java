package si.konferenca.registration.api;

import java.io.Serial;

/** The body is not a valid JSON object for the operation (unknown property or wrong type). */
class MalformedRequestException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  MalformedRequestException() {
    super("malformed request");
  }
}
