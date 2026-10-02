package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * US-006 Participant email confirmation and US-007 Organizer notification, through the email
 * contract and the mail catcher.
 */
class Us006Us007EmailAcceptanceTest extends AcceptanceTestBase {

  private static Mailbox.Mail only(List<Mailbox.Mail> mails) {
    assertThat(mails).hasSize(1);
    return mails.get(0);
  }

  private static Mailbox.Mail organizerMail(List<Mailbox.Mail> mails) {
    return only(Mailbox.sentTo(mails, Stack.ORGANIZER_EMAILS.get(0)));
  }

  @Test
  void ac_006_01_participantReceivesAConfirmationEmail() {
    UUID id = assertAccepted(Api.register(Stack.app(), external()));

    Mailbox.Mail mail = only(Mailbox.sentTo(Mailbox.awaitMessages(2), "ana.novak@example.org"));
    assertThat(mail.to()).containsExactly("ana.novak@example.org");
    assertThat(mail.from()).isEqualTo(Stack.MAIL_FROM);
    assertThat(mail.subject()).isEqualTo("Potrditev prijave: " + Stack.CONFERENCE_NAME);
    assertThat(mail.html()).isEmpty();
    assertThat(mail.attachments()).isEmpty();
    assertThat(mail.text())
        .contains(
            id.toString(),
            "Ana",
            "Novak",
            "ana.novak@example.org",
            "Podjetje Primer",
            "Delavnica: testiranje programske opreme",
            "Kosilo, prvi dan");
  }

  @Test
  void ac_006_01_studentReceivesAConfirmationEmailWithSlovenianCharacters() {
    Map<String, Object> registration = student();
    registration.put("firstName", "Žan");
    registration.put("lastName", "Šuštar Čeh");
    UUID id = assertAccepted(Api.register(Stack.app(), registration));

    Mailbox.Mail mail = only(Mailbox.sentTo(Mailbox.awaitMessages(2), "luka.kovac@example.org"));
    assertThat(mail.to()).containsExactly("luka.kovac@example.org");
    assertThat(mail.text())
        .contains(
            id.toString(),
            "Žan",
            "Šuštar Čeh",
            "Univerza v Ljubljani",
            "Računalništvo in informatika",
            "63210001",
            "Otvoritvena slovesnost",
            "Voden ogled mesta");
  }

  @Test
  void ac_006_02_ac_007_03_rejectedRegistrationSendsNoEmail() {
    Api.Reply reply = Api.register(Stack.app(), with(external(), "consent", false));

    assertThat(reply.status()).isEqualTo(400);
    assertThat(Mailbox.messagesAfterSettling()).isEmpty();
  }

  @Test
  void ac_006_03_ac_007_04_registrationStaysAcceptedWhenEmailsCannotBeSent() throws IOException {
    Path directory = Stack.newDirectory("json-copies-no-mail");
    Map<String, String> settings =
        Map.of(
            "JSON_COPY_DIR", directory.toString(),
            "SMTP_HOST", "127.0.0.1",
            "SMTP_PORT", String.valueOf(Stack.closedPort()));
    try (Stack.App app = Stack.startApp(settings)) {
      Api.Reply reply = Api.register(app, external());

      UUID id = assertAccepted(reply);
      assertThat(registrationRow(id).get("email")).isEqualTo("ana.novak@example.org");
      assertThat(directory.resolve(id + ".json")).isRegularFile();
      assertThat(Mailbox.messagesAfterSettling()).isEmpty();
    }
  }

  @Test
  void ac_007_01_everyOrganizerRecipientIsNotifiedWithTheSubmittedData() {
    UUID id = assertAccepted(Api.register(Stack.app(), external()));

    Mailbox.Mail mail = organizerMail(Mailbox.awaitMessages(2));
    assertThat(mail.to()).containsExactlyInAnyOrderElementsOf(Stack.ORGANIZER_EMAILS);
    assertThat(mail.from()).isEqualTo(Stack.MAIL_FROM);
    assertThat(mail.subject())
        .isEqualTo("Nova prijava: " + Stack.CONFERENCE_NAME + " (" + id + ")");
    assertThat(mail.html()).isEmpty();
    assertThat(mail.text())
        .contains(
            id.toString(),
            "Ana",
            "Novak",
            "ana.novak@example.org",
            "Podjetje Primer",
            "Delavnica: testiranje programske opreme",
            "Kosilo, prvi dan");
  }

  @Test
  void ac_007_01_organizerNotificationContainsStudentData() {
    UUID id = assertAccepted(Api.register(Stack.app(), student()));

    Mailbox.Mail mail = organizerMail(Mailbox.awaitMessages(2));
    assertThat(mail.text())
        .contains(
            id.toString(),
            "Luka",
            "Kovač",
            "luka.kovac@example.org",
            "Univerza v Ljubljani",
            "Računalništvo in informatika",
            "63210001",
            "Otvoritvena slovesnost",
            "Voden ogled mesta");
  }

  @Test
  void ac_007_02_organizerNotificationHasTheRawJsonAttached() throws IOException {
    UUID id = assertAccepted(Api.register(Stack.app(), external()));

    Mailbox.Mail mail = organizerMail(Mailbox.awaitMessages(2));
    assertThat(mail.attachments()).hasSize(1);
    Mailbox.Attachment attachment = mail.attachments().get(0);
    assertThat(attachment.fileName()).isEqualTo("registration-" + id + ".json");
    assertThat(attachment.contentType()).startsWith("application/json");
    byte[] storedCopy = Files.readAllBytes(Stack.app().jsonCopyDir().resolve(id + ".json"));
    assertThat(mail.attachmentBytes(attachment)).isEqualTo(storedCopy);
  }
}
