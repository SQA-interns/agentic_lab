package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.student;
import static si.konferenca.registration.acceptance.support.Registrations.with;

import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import si.konferenca.registration.acceptance.support.AcceptanceStack;
import si.konferenca.registration.acceptance.support.Mailpit;
import si.konferenca.registration.acceptance.support.Response;
import si.konferenca.registration.acceptance.support.RunningApp;

/** US-007 Organizer notification with the raw JSON attached, observed in the mail catcher. */
@ExtendWith(OutputCaptureExtension.class)
class Us007OrganizerNotificationAcceptanceTest {

  @Test
  @DisplayName("AC-007-01 every organizer recipient receives the submitted data")
  void ac007_01_organizersReceiveSubmittedData() {
    Map<String, Object> request = student();
    String email = (String) request.get("email");

    Response response = AcceptanceStack.shared().api().register(request);

    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    Mailpit mailpit = AcceptanceStack.mailpit();
    for (String organizer : AcceptanceStack.ORGANIZER_EMAILS) {
      await()
          .atMost(Duration.ofSeconds(20))
          .untilAsserted(() -> assertThat(mailpit.searchTo(organizer, email)).hasSize(1));
      Mailpit.Message message = mailpit.searchTo(organizer, email).get(0);
      assertThat(message.subject())
          .isEqualTo("New registration (STUDENT): " + AcceptanceStack.CONFERENCE_NAME);
      assertThat(message.text())
          .contains("Luka")
          .contains("Horvat")
          .contains(email)
          .contains("Univerza v Mariboru")
          .contains("Informatika")
          .contains("E1234567")
          .contains("Career fair");
    }
  }

  @Test
  @DisplayName("AC-007-02 the organizer email has the stored raw JSON copy attached")
  void ac007_02_organizerEmailCarriesStoredJsonCopy() throws Exception {
    Map<String, Object> request = external();
    String email = (String) request.get("email");

    Response response = AcceptanceStack.shared().api().register(request);

    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    String id = response.json().path("id").asString();
    String organizer = AcceptanceStack.ORGANIZER_EMAILS.get(0);
    Mailpit mailpit = AcceptanceStack.mailpit();
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(() -> assertThat(mailpit.searchTo(organizer, email)).hasSize(1));
    Mailpit.Message message = mailpit.searchTo(organizer, email).get(0);
    assertThat(message.attachments()).hasSize(1);
    Mailpit.Attachment attachment = message.attachments().get(0);
    assertThat(attachment.fileName()).isEqualTo("registration-" + id + ".json");
    assertThat(attachment.contentType()).startsWith("application/json");
    byte[] stored = Files.readAllBytes(AcceptanceStack.jsonCopyDir().resolve(id + ".json"));
    assertThat(attachment.content()).isEqualTo(stored);
  }

  @Test
  @DisplayName("AC-007-03 a failed organizer email leaves the registration accepted")
  void ac007_03_organizerEmailFailureKeepsRegistration(CapturedOutput output) {
    Map<String, Object> request = external();
    request.put("firstName", "Neposlanka");
    String email = (String) request.get("email");

    try (RunningApp app =
        AcceptanceStack.start(Map.of("SMTP_PORT", String.valueOf(AcceptanceStack.closedPort())))) {
      Response response = app.api().register(request);

      assertThat(response.status()).as(response.toString()).isEqualTo(201);
      String id = response.json().path("id").asString();
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () ->
                  assertThat(Us006ParticipantEmailAcceptanceTest.warningLines(output, id))
                      .isNotEmpty());
      Us006ParticipantEmailAcceptanceTest.sleep(Duration.ofSeconds(1));
    }
    assertThat(AcceptanceStack.db().registrationByEmail(email)).isPresent();
    assertThat(output.getAll()).doesNotContain(email).doesNotContain("Neposlanka");
  }

  @Test
  @DisplayName("AC-007-04 no organizer email is sent for a rejected registration")
  void ac007_04_rejectedRegistrationSendsNoOrganizerEmail() {
    Map<String, Object> request = with(external(), "optionIds", List.of("no-such-option"));
    String email = (String) request.get("email");

    Response response = AcceptanceStack.shared().api().register(request);

    assertThat(response.status()).as(response.toString()).isEqualTo(400);
    Us006ParticipantEmailAcceptanceTest.sleep(Duration.ofSeconds(3));
    assertThat(AcceptanceStack.mailpit().search(email)).isEmpty();
  }
}
