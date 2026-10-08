package si.konferenca.registration.api;

/** The body is not a JSON registration request (400 malformed). */
class MalformedRequestException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  MalformedRequestException() {
    super("malformed request", null, false, false);
  }
}
