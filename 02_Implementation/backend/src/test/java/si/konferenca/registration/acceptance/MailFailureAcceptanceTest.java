package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * AC-006-04 and AC-007-03 — an SMTP server that cannot be reached must not undo an accepted
 * registration (decision D-3). This class points the application at a closed local port.
 */
class MailFailureAcceptanceTest extends AcceptanceHarness {

  private static final int CLOSED_PORT = findClosedPort();

  private static int findClosedPort() {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @DynamicPropertySource
  static void unreachableSmtp(DynamicPropertyRegistry registry) {
    registerProperties(
        registry,
        Map.<String, Supplier<Object>>of(
            "spring.mail.host", () -> "localhost", "spring.mail.port", () -> CLOSED_PORT));
  }

  @Autowired private Environment environment;

  @Test
  void harnessPointsTheApplicationAtAClosedSmtpPort() {
    assertThat(environment.getProperty("spring.mail.port")).isEqualTo(String.valueOf(CLOSED_PORT));
  }

  @Test
  void ac_006_04_and_ac_007_03_mailFailureDoesNotUndoAcceptedRegistration() {
    String email = uniqueEmail("ac00604");

    Resp r = register(validExternal(email, "ws-ai"));

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    String id = r.json().path("registrationId").asText();
    assertThat(countRegistrationsByEmail(email)).isEqualTo(1);
    assertThat(registrationRow(id).get("email")).isEqualTo(email);
    assertThat(backupFileOf(id)).exists();
  }
}
