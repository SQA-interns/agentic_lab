package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

/** US-006 — Participant email confirmation (specification §11). */
class ParticipantEmailAcceptanceTest extends AcceptanceTestBase {

  @Test
  void ac_006_01_participantReceivesConfirmationEmail() {
    String email = uniqueEmail("ac00601");
    Resp r = register(validExternal(email));
    assertThat(r.status()).as(r.text()).isEqualTo(201);

    Mailbox.Mail mail = Mailbox.awaitMail(email, m -> true);

    assertThat(mail.to()).containsExactly(email);
    assertThat(mail.subject()).isEqualTo("Registration confirmation");
  }

  @Test
  void ac_006_02_confirmationEmailListsNameTypeAndOptionsWithUnicode() {
    String email = uniqueEmail("ac00602");
    ObjectNode body = validStudent(email, "ws-ai", "other-tour");
    body.put("firstName", "Živa");
    body.put("lastName", "Čepič");
    Resp r = register(body);
    assertThat(r.status()).as(r.text()).isEqualTo(201);
    String id = r.json().path("registrationId").asText();

    Mailbox.Mail mail = Mailbox.awaitMail(email, m -> true);

    assertThat(mail.contentType()).startsWith("text/plain");
    assertThat(mail.text())
        .contains("Živa")
        .contains("Čepič")
        .contains("Acceptance Conference")
        .contains(id)
        .contains("Student")
        .contains("Delavnica: umetna inteligenca")
        .contains("Ogled Ljubljane (city tour)");
  }

  @Test
  void ac_006_02_confirmationEmailWithoutOptionsSaysSo() {
    String email = uniqueEmail("ac00602b");
    Resp r = register(validExternal(email));
    assertThat(r.status()).isEqualTo(201);

    Mailbox.Mail mail = Mailbox.awaitMail(email, m -> true);

    assertThat(mail.text()).contains("External participant");
    assertThat(mail.text()).contains("No optional activities selected.");
  }

  @Test
  void ac_006_03_noEmailForRejectedSubmission() {
    String email = uniqueEmail("ac00603");
    ObjectNode body = validExternal(email);
    body.remove("organization");
    assertThat(register(body).status()).isEqualTo(400);

    assertThat(Mailbox.afterGrace(email, m -> true)).isEmpty();
  }

  @Test
  void ac_006_02_participantTextCannotInjectMarkupOrHeaders() {
    String email = uniqueEmail("ac00602c");
    ObjectNode body = validExternal(email);
    body.put("organization", "<b>ACME</b> <script>alert(1)</script>");
    Resp r = register(body);
    assertThat(r.status()).as(r.text()).isEqualTo(201);

    Mailbox.Mail mail = Mailbox.awaitMail(email, m -> true);

    assertThat(mail.contentType()).startsWith("text/plain");
    assertThat(mail.subject()).isEqualTo("Registration confirmation");
  }
}
