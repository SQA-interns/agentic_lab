package si.konferenca.registration.acceptance.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The acceptance environment with an SMTP port where nothing listens (D-13). */
public abstract class MailUnavailableTestBase extends AcceptanceTestBase {

  private static final int CLOSED_SMTP_PORT = AcceptanceEnvironment.closedPort();

  // D-21: bound property names, because the base class's environment-style keys win over a
  // subclass registering the same keys.
  @DynamicPropertySource
  static void unreachableMail(DynamicPropertyRegistry registry) {
    registry.add("app.mail.host", () -> "127.0.0.1");
    registry.add("app.mail.port", () -> String.valueOf(CLOSED_SMTP_PORT));
  }
}
