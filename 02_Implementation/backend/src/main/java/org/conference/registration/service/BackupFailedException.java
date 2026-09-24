package org.conference.registration.service;

/** The JSON backup could not be written; the registration must not be reported as successful. */
public class BackupFailedException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public BackupFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
