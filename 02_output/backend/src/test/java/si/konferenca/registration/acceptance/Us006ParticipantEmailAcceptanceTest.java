package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Api.awaitMails;
import static si.konferenca.registration.acceptance.support.Api.exportRowsFor;
import static si.konferenca.registration.acceptance.support.Api.external;
import static si.konferenca.registration.acceptance.support.Api.mailDetail;
import static si.konferenca.registration.acceptance.support.Api.mailsAfter;
import static si.konferenca.registration.acceptance.support.Api.recipients;
import static si.konferenca.registration.acceptance.support.Api.register;
import static si.konferenca.registration.acceptance.support.Api.student;
import static si.konferenca.registration.acceptance.support.Api.uniqueEmail;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.Api.Response;
import si.konferenca.registration.acceptance.support.Backend;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** US-006 Participant email confirmation, observed in the Mailpit mail catcher. */
class Us006ParticipantEmailAcceptanceTest {

  private static Backend backend;

  @BeforeAll
  static void start() {
    backend = Backend.startDefault();
  }

  @AfterAll
  static void stop() {
    backend.close();
  }

  static int closedPort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static List<JsonNode> participantMails(String email) {
    return awaitMails(email, 1).stream().filter(m -> recipients(m).contains(email)).toList();
  }

  @Test
  void AC_006_01_participantReceivesOneConfirmationEmailWithTheSubmittedData() {
    String email = uniqueEmail();
    Response response = register(backend, external(email));
    assertThat(response.status()).as(response.text()).isEqualTo(201);

    awaitMails(email, 2);
    List<JsonNode> mails = participantMails(email);

    assertThat(mails).hasSize(1);
    JsonNode detail = mailDetail(mails.getFirst());
    assertThat(recipients(detail)).containsExactly(email);
    assertThat(detail.get("Subject").asString())
        .isEqualTo("Registration confirmed: " + Backend.CONFERENCE_NAME);
    assertThat(detail.get("Text").asString())
        .contains("Ana", "Novak", email, "Institut Jožef Stefan")
        .contains("Delavnica: testiranje sistemov UI", "Lunch, day 1");
    assertThat(detail.path("Attachments").size()).isZero();
  }

  @Test
  void AC_006_02_rejectedRegistrationSendsNoEmail() {
    String email = uniqueEmail();
    ObjectNode body = external(email);
    body.put("firstName", "");

    Response response = register(backend, body);

    assertThat(response.status()).isEqualTo(400);
    assertThat(mailsAfter(email, 2000)).isEmpty();
  }

  @Test
  void AC_006_03_failingParticipantEmailKeepsTheRegistration() throws IOException {
    try (Backend noMail =
        Backend.builder()
            .setting("SMTP_HOST", "127.0.0.1")
            .setting("SMTP_PORT", String.valueOf(closedPort()))
            .start()) {
      String email = uniqueEmail();

      Response response = register(noMail, external(email));

      assertThat(response.status()).as(response.text()).isEqualTo(201);
      assertThat(exportRowsFor(noMail, email)).hasSize(1);
      String id = response.json().get("id").asString();
      assertThat(noMail.jsonCopyDir().resolve(id + ".json")).exists();
    }
  }

  @Test
  void AC_006_04_slovenianCharactersAreUnchangedInTheEmail() {
    String email = uniqueEmail();
    ObjectNode body = student(email);
    body.put("firstName", "Žan");
    body.put("lastName", "Čebašek");
    body.put("studyProgramme", "Računalništvo in informatika");

    assertThat(register(backend, body).status()).isEqualTo(201);

    awaitMails(email, 2);
    JsonNode detail = mailDetail(participantMails(email).getFirst());
    assertThat(detail.get("Text").asString())
        .contains("Žan", "Čebašek", "Računalništvo in informatika");
  }
}
