package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Fixtures;
import si.konferenca.registration.acceptance.support.Mailpit;
import si.konferenca.registration.acceptance.support.TestInfrastructure;
import tools.jackson.databind.JsonNode;

/** US-006 participant confirmation email and US-007 organizer notification. */
class EmailAcceptanceTest extends AcceptanceTestBase {

  private static final List<String> COPY_KEYS =
      List.of(
          "schemaVersion",
          "reference",
          "type",
          "submittedAt",
          "participant",
          "options",
          "consents");

  @Test
  @DisplayName("AC-006-01 the participant receives a confirmation email")
  void ac00601ParticipantReceivesConfirmation() {
    String email = Fixtures.uniqueEmail();

    Api.Response r = api.register(Fixtures.external(email));

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    String reference = r.json().path("reference").asString();
    List<Mailpit.Message> messages = mailpit.awaitMessages(email, reference, 1);
    assertThat(messages).hasSize(1);
    Mailpit.Message m = messages.get(0);
    assertThat(m.to()).containsExactly(email);
    assertThat(m.subject()).contains(TestInfrastructure.CONFERENCE_NAME);
    assertThat(m.text())
        .contains(TestInfrastructure.CONFERENCE_NAME)
        .contains("Ana")
        .contains("Novak")
        .contains(reference)
        .contains("Workshop: Secure web development")
        .contains("Lunch, day 1");
  }

  @Test
  @DisplayName("AC-006-02 markup in text fields appears literally in a plain-text email")
  void ac00602MarkupIsNotInterpreted() {
    String email = Fixtures.uniqueEmail();
    Map<String, Object> request = Fixtures.external(email);
    request.put("firstName", "<b>Ana</b>");
    request.put("organization", "<script>alert(1)</script> d.o.o.");

    Api.Response r = api.register(request);

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    String reference = r.json().path("reference").asString();
    Mailpit.Message m = mailpit.awaitMessages(email, reference, 1).get(0);
    assertThat(m.text()).contains("<b>Ana</b>");
    assertThat(m.html()).isEmpty();
    Mailpit.Message org =
        mailpit.awaitMessages(TestInfrastructure.ORGANIZER_EMAIL_1, reference, 1).get(0);
    assertThat(org.text()).contains("<script>alert(1)</script> d.o.o.");
    assertThat(org.html()).isEmpty();
  }

  @Test
  @DisplayName("AC-006-02 line breaks in text fields cannot add headers or recipients")
  void ac00602HeaderInjectionIsPrevented() {
    String email = Fixtures.uniqueEmail();
    String injected = "inj-" + email;
    Map<String, Object> request = Fixtures.external(email);
    request.put("lastName", "Novak\r\nBcc: " + injected);
    request.put("organization", "Org\nSubject: hacked");

    Api.Response r = api.register(request);

    if (r.status() == 201) {
      String reference = r.json().path("reference").asString();
      Mailpit.Message m = mailpit.awaitMessages(email, reference, 1).get(0);
      assertThat(m.to()).containsExactly(email);
      assertThat(m.cc()).isEmpty();
      assertThat(m.bcc()).isEmpty();
      assertThat(m.subject()).doesNotContain("hacked");
      assertThat(mailpit.headers(m.id()).toString()).doesNotContain(injected);
    } else {
      assertThat(r.status()).as(r.text()).isEqualTo(400);
      assertThat(r.errorFields()).containsAnyOf("lastName", "organization");
      assertNothingStored(email);
    }
    sleep(Duration.ofSeconds(1));
    assertThat(mailpit.messagesTo(injected)).isEmpty();
  }

  @Test
  @DisplayName("AC-006-03 when the mail server fails, the registration is kept and mailed later")
  void ac00603MailIsRetriedAfterFailure() {
    String email = Fixtures.uniqueEmail();
    mailpit.failAllRecipients(true);
    String reference;
    try {
      Api.Response r = api.register(Fixtures.external(email));

      assertThat(r.status()).as(r.text()).isEqualTo(201);
      reference = r.json().path("reference").asString();
      assertThat(db.registrationByReference(reference)).isNotNull();
      assertThat(jsonCopy(jsonCopyDir(), reference)).isRegularFile();
      sleep(Duration.ofSeconds(3));
      assertThat(mailpit.messagesTo(email)).isEmpty();
    } finally {
      mailpit.failAllRecipients(false);
    }

    assertThat(mailpit.awaitMessages(email, reference, 1)).hasSize(1);
    assertThat(mailpit.awaitMessages(TestInfrastructure.ORGANIZER_EMAIL_1, reference, 1))
        .hasSize(1);
  }

  @Test
  @DisplayName("AC-007-01 every organizer receives the data with the JSON copy attached")
  void ac00701OrganizersReceiveNotificationWithJsonAttachment() {
    String email = Fixtures.uniqueEmail();

    Api.Response r = api.register(Fixtures.student(email));

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    String reference = r.json().path("reference").asString();
    byte[] copy = readBytes(jsonCopy(jsonCopyDir(), reference));
    for (String organizer :
        List.of(TestInfrastructure.ORGANIZER_EMAIL_1, TestInfrastructure.ORGANIZER_EMAIL_2)) {
      Mailpit.Message m = mailpit.awaitMessages(organizer, reference, 1).get(0);
      assertThat(m.subject()).contains(TestInfrastructure.CONFERENCE_NAME);
      assertThat(m.text())
          .contains("Luka")
          .contains("Kovač")
          .contains(email)
          .contains("Univerza v Ljubljani")
          .contains("Računalništvo in informatika")
          .contains("63200001")
          .contains("Student career fair");
      assertThat(m.attachments()).hasSize(1);
      JsonNode attachment = m.attachments().get(0);
      assertThat(attachment.path("FileName").asString())
          .isEqualTo("registration-" + reference + ".json");
      byte[] attached = mailpit.part(m.id(), attachment.path("PartID").asString());
      assertThat(attached).isEqualTo(copy);
    }
  }

  @Test
  @DisplayName("AC-007-02 the organizer email contains only registration data")
  void ac00702OrganizerEmailContainsOnlyRegistrationData() {
    String email = Fixtures.uniqueEmail();

    Api.Response r = api.register(Fixtures.external(email));

    assertThat(r.status()).as(r.text()).isEqualTo(201);
    String reference = r.json().path("reference").asString();
    Mailpit.Message m =
        mailpit.awaitMessages(TestInfrastructure.ORGANIZER_EMAIL_1, reference, 1).get(0);
    JsonNode json =
        Api.JSON.readTree(mailpit.part(m.id(), m.attachments().get(0).path("PartID").asString()));
    Iterator<String> names = json.propertyNames().iterator();
    while (names.hasNext()) {
      assertThat(COPY_KEYS).contains(names.next());
    }
    assertThat(m.text())
        .doesNotContainIgnoringCase("mail_status")
        .doesNotContain("PENDING")
        .doesNotContain("FAILED")
        .doesNotContain("registration_id")
        .doesNotContain(TestInfrastructure.ORGANIZER_PASSWORD);
  }

  private static byte[] readBytes(java.nio.file.Path file) {
    try {
      return java.nio.file.Files.readAllBytes(file);
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }
}
