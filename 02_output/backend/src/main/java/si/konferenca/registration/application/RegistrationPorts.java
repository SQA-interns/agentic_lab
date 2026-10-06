package si.konferenca.registration.application;

import java.util.List;
import java.util.UUID;
import si.konferenca.registration.domain.Registration;

/** Ports of the registration use cases, implemented in infrastructure. */
public final class RegistrationPorts {

  private RegistrationPorts() {}

  /** Registration storage in the database (BR-07). */
  public interface RegistrationRepository {

    boolean existsByNormalizedEmail(String normalizedEmail);

    /**
     * Inserts and flushes the registration in the current transaction.
     *
     * @throws DuplicateEmailException when the email is already registered
     */
    void insert(Registration registration);

    /** All registrations, oldest first, for the export. */
    List<Registration> findAll();
  }

  /** Raw JSON copies on persistent storage (BR-07). */
  public interface JsonCopyStore {

    /** Writes the copy atomically and returns its bytes. */
    byte[] write(Registration registration);

    void delete(UUID registrationId);
  }

  /** Runs work in one database transaction; a failed commit throws. */
  public interface Transactions {
    void inTransaction(Runnable work);
  }

  /** Anti-automation verification (SR-01). */
  public interface CaptchaVerifier {
    CaptchaResult verify(String token);
  }

  /** Verification outcome. */
  public enum CaptchaResult {
    PASSED,
    FAILED,
    UNAVAILABLE
  }

  /** Sends the emails of an accepted registration without blocking or throwing (D-07). */
  public interface Notifier {
    void registrationAccepted(Registration registration, byte[] jsonCopy);
  }

  /** Writes the export workbook (US-008). */
  public interface ExportWriter {
    byte[] write(List<Registration> registrations);
  }

  /** The email is already registered (D-09). */
  public static final class DuplicateEmailException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DuplicateEmailException() {
      super("email already registered");
    }
  }
}
