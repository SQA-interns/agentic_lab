package si.konferenca.registration.api;

import java.io.Serial;

/** Organizer credentials over plain HTTP from a non-local client (SR-06). */
class HttpsRequiredException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  HttpsRequiredException() {
    super("https required");
  }
}
