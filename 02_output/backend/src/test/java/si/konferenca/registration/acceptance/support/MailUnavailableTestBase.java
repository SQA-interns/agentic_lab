package si.konferenca.registration.acceptance.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The acceptance environment with an SMTP port where nothing listens (D-13). */
public abstract class MailUnavailableTestBase extends AcceptanceTestBase {

  private static final int CLOSED_SMTP_PORT = AcceptanceEnvironment.closedPort();

  @DynamicPropertySource
  static void unreachableMail(DynamicPropertyRegistry registry) {
    registry.add("MAIL_HOST", () -> "127.0.0.1");
    registry.add("MAIL_PORT", () -> String.valueOf(CLOSED_SMTP_PORT));
  }
}
