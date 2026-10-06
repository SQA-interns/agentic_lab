package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Payloads.student;

import java.util.Map;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.MailUnavailableTestBase;

/** US-007 with an unreachable mail server (D-13). */
class Us007OrganizerNotificationFailureAcceptanceTest extends MailUnavailableTestBase {

  @Test
  void AC_007_05_registration_stays_accepted_when_the_organizer_email_cannot_be_sent() {
    Map<String, Object> body = student();

    assertAccepted(api.register(body));

    assertThat(db.registrationByEmail((String) body.get("email"))).isNotNull();
    assertThat(copies.copies()).hasSize(1);
  }
}
