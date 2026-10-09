package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.options;
import static si.konferenca.registration.acceptance.support.Registrations.student;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.ApiClient;
import si.konferenca.registration.acceptance.support.Mailpit;
import si.konferenca.registration.acceptance.support.TestStack;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** US-007 Organizer notification, as caught by Mailpit. */
class OrganizerNotificationAcceptanceTest extends AcceptanceTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Test
  void ac_007_01_everyOrganizerAddressReceivesTheSubmittedData() {
    ObjectNode request = options(external(), "ws-ai", "meal-lunch-1");

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    String id = response.json().path("registrationId").asString();
    for (String organizer : TestStack.ORGANIZER_EMAILS) {
      Mailpit.Message message = mail.awaitMessagesTo(organizer, 1).get(0);
      assertThat(message.subject()).isEqualTo("New registration: " + TestStack.CONFERENCE_NAME);
      assertThat(message.from()).isEqualTo(TestStack.MAIL_FROM);
      assertThat(message.text())
          .contains("Registration ID: " + id)
          .contains("Registration type: External participant")
          .contains("First name: Ana")
          .contains("Last name: Novak")
          .contains("Email: " + request.path("email").asString())
          .contains("Organization: Institut Jožef Stefan")
          .contains("Delavnica umetne inteligence")
          .contains("Lunch, day 1")
          .contains("data-processing");
    }
  }

  @Test
  void ac_007_01_studentNotificationContainsTheStudentFields() {
    ObjectNode request = student();

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    Mailpit.Message message = mail.awaitMessagesTo(TestStack.ORGANIZER_EMAILS.get(0), 1).get(0);
    assertThat(message.text())
        .contains("Registration type: Student")
        .contains("Study institution: Fakulteta za računalništvo in informatiko")
        .contains("Study programme: Računalništvo in informatika")
        .contains("Student ID: 63200001");
  }

  @Test
  void ac_007_02_rawRegistrationJsonIsAttachedIdenticalToTheStoredCopy() {
    ApiClient.Response response = api.register(external());

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    String id = response.json().path("registrationId").asString();
    Mailpit.Message message = mail.awaitMessagesTo(TestStack.ORGANIZER_EMAILS.get(0), 1).get(0);
    assertThat(message.attachments()).hasSize(1);
    Mailpit.Attachment attachment = message.attachments().get(0);
    assertThat(attachment.fileName()).isEqualTo("registration-" + id + ".json");
    assertThat(attachment.contentType()).startsWith("application/json");
    assertThat(mail.attachment(message.id(), attachment.partId())).isEqualTo(copies.bytesOf(id));
  }

  @Test
  void ac_007_03_notificationContainsOnlyRegistrationData() {
    ObjectNode request = external();
    String email = "Mixed.Case." + UUID.randomUUID().toString().substring(0, 8) + "@Example.si";
    request.put("email", email);

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    Mailpit.Message message = mail.awaitMessagesTo(TestStack.ORGANIZER_EMAILS.get(0), 1).get(0);
    assertThat(message.text())
        .contains(email)
        .doesNotContain(email.toLowerCase())
        .doesNotContain(TestStack.CAPTCHA_TOKEN)
        .doesNotContainIgnoringCase("captcha")
        .doesNotContain("127.0.0.1")
        .doesNotContain("email_normalized")
        .doesNotContain(TestStack.ORGANIZER_PASSWORD);
    String attachment =
        new String(
            mail.attachment(message.id(), message.attachments().get(0).partId()),
            StandardCharsets.UTF_8);
    assertThat(attachment).doesNotContainIgnoringCase("captcha").doesNotContain("127.0.0.1");
  }

  @Test
  void ac_007_04_slovenianCharactersSurviveInBodyAndAttachment() {
    ObjectNode request = external();
    request.put("firstName", "Žiga");
    request.put("lastName", "Čuček Šantl");
    request.put("organization", "Zavod za šolstvo");

    ApiClient.Response response = api.register(request);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    Mailpit.Message message = mail.awaitMessagesTo(TestStack.ORGANIZER_EMAILS.get(0), 1).get(0);
    assertThat(message.text())
        .contains("First name: Žiga")
        .contains("Last name: Čuček Šantl")
        .contains("Organization: Zavod za šolstvo");
    JsonNode attachment =
        JSON.readTree(mail.attachment(message.id(), message.attachments().get(0).partId()));
    assertThat(attachment.path("participant").path("firstName").asString()).isEqualTo("Žiga");
    assertThat(attachment.path("participant").path("lastName").asString()).isEqualTo("Čuček Šantl");
    assertThat(attachment.path("participant").path("organization").asString())
        .isEqualTo("Zavod za šolstvo");
  }
}
