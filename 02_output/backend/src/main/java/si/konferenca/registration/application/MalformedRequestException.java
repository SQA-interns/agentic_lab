package si.konferenca.registration.application;

/** The request does not have the contract's structure. */
public class MalformedRequestException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public MalformedRequestException(String message) {
    super(message);
  }
}
