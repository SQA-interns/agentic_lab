package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.options;
import static si.konferenca.registration.acceptance.support.Registrations.student;

import java.util.List;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.ApiClient;
import si.konferenca.registration.acceptance.support.Mailpit;
import si.konferenca.registration.acceptance.support.TestStack;
import tools.jackson.databind.node.ObjectNode;

/** US-006 Participant email confirmation, as caught by Mailpit. */
class ParticipantEmailAcceptanceTest extends AcceptanceTest {

  @Test
  void ac_006_01_participantReceivesAConfirmationWithNameTypeAndOptions() {
    ObjectNode request = options(external(), "ws-ai", "ev-reception");
    String email = request.path("email").asString();

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    String id = response.json().path("registrationId").asString();
    List<Mailpit.Message> messages = mail.awaitMessagesTo(email, 1);
    assertThat(messages).hasSize(1);
    Mailpit.Message message = messages.get(0);
    assertThat(message.from()).isEqualTo(TestStack.MAIL_FROM);
    assertThat(message.to()).containsExactly(email);
    assertThat(message.subject())
        .isEqualTo("Registration confirmation: " + TestStack.CONFERENCE_NAME);
    assertThat(message.text())
        .contains("Ana Novak")
        .contains("Registration type: External participant")
        .contains("Workshops")
        .contains("Delavnica umetne inteligence")
        .contains("Events")
        .contains("Welcome reception")
        .contains("Registration ID: " + id);
    assertThat(message.attachments()).isEmpty();
  }

  @Test
  void ac_006_01_studentConfirmationNamesTheStudentType() {
    ObjectNode request = options(student());
    String email = request.path("email").asString();

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    Mailpit.Message message = mail.awaitMessagesTo(email, 1).get(0);
    assertThat(message.text())
        .contains("Luka Kranjc")
        .contains("Registration type: Student")
        .contains("No options selected");
  }

  @Test
  void ac_006_02_slovenianCharactersInTheNameAppearUnchanged() {
    ObjectNode request = external();
    request.put("firstName", "Črtomir");
    request.put("lastName", "Šuštar Žižek");
    String email = request.path("email").asString();

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    Mailpit.Message message = mail.awaitMessagesTo(email, 1).get(0);
    assertThat(message.text()).contains("Črtomir Šuštar Žižek");
  }

  @Test
  void ac_006_03_markupIsShownAsPlainTextAndAddsNoRecipientOrHeader() {
    ObjectNode request = external();
    request.put("firstName", "<b>Ana</b>");
    request.put("lastName", "O'Neil & \"Co\" <script>alert(1)</script>");
    request.put("organization", "<a href=\"https://evil.example\">Org</a>");
    String email = request.path("email").asString();

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    Mailpit.Message message = mail.awaitMessagesTo(email, 1).get(0);
    assertThat(message.text()).contains("<b>Ana</b> O'Neil & \"Co\" <script>alert(1)</script>");
    assertThat(message.html()).isEmpty();
    assertThat(message.to()).containsExactly(email);
    assertThat(message.cc()).isEmpty();
    assertThat(message.bcc()).isEmpty();
    assertThat(message.subject())
        .isEqualTo("Registration confirmation: " + TestStack.CONFERENCE_NAME);
    assertThat(mail.headers(message.id()).toString()).doesNotContain("evil.example");
  }

  @Test
  void ac_006_05_rejectedRegistrationSendsNoEmail() {
    ObjectNode request = external();
    request.put("lastName", "");

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(400);
    assertNoEmail();
  }
}
