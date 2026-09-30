package lab.conference.platform;

import java.io.IOException;

/** Raised while reading a request body that exceeds the configured maximum size. */
public class PayloadTooLargeException extends IOException {

  private static final long serialVersionUID = 1L;

  public PayloadTooLargeException() {
    super("request body too large");
  }
}
