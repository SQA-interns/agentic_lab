package si.konferenca.registration.web.filter;

import java.io.IOException;

/** Raised while reading a request body that exceeds the configured size limit. */
public class PayloadTooLargeException extends IOException {

  private static final long serialVersionUID = 1L;

  public PayloadTooLargeException() {
    super("Request body exceeds the configured limit");
  }
}
