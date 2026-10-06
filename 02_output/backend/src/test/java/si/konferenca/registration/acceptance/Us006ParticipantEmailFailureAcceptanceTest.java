package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Payloads.external;

import java.util.Map;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.MailUnavailableTestBase;

/** US-006 with an unreachable mail server (D-13). */
class Us006ParticipantEmailFailureAcceptanceTest extends MailUnavailableTestBase {

  @Test
  void AC_006_04_registration_stays_accepted_when_the_participant_email_cannot_be_sent() {
    Map<String, Object> body = external();

    assertAccepted(api.register(body));

    assertThat(db.registrationByEmail((String) body.get("email"))).isNotNull();
    assertThat(copies.copies()).hasSize(1);
  }
}
