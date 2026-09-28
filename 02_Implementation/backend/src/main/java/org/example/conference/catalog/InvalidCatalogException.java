package org.example.conference.catalog;

/** Thrown at startup when the catalog configuration is unreadable or invalid (fail fast). */
public class InvalidCatalogException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidCatalogException(String message) {
    super(message);
  }

  public InvalidCatalogException(String message, Throwable cause) {
    super(message, cause);
  }
}
