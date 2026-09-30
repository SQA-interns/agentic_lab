package lab.conference.options;

/** The catalog configuration is missing or invalid; startup must stop. */
public class InvalidCatalogException extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  public InvalidCatalogException(String message) {
    super(message);
  }

  public InvalidCatalogException(String message, Throwable cause) {
    super(message, cause);
  }
}
