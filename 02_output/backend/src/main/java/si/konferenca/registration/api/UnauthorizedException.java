package si.konferenca.registration.api;

import java.io.Serial;

/** Missing or wrong organizer credentials or token. */
class UnauthorizedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  UnauthorizedException() {
    super("unauthorized");
  }
}
