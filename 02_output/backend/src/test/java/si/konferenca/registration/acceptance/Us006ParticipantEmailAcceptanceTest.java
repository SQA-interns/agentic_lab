package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.with;

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

/** US-006 Participant email confirmation, observed in the mail catcher. */
@ExtendWith(OutputCaptureExtension.class)
class Us006ParticipantEmailAcceptanceTest {

  @Test
  @DisplayName("AC-006-01 one confirmation email is sent to the participant")
  void ac006_01_participantReceivesOneConfirmationEmail() {
    Map<String, Object> request = external();
    request.put("firstName", "Špela");
    request.put("lastName", "Žagar");
    String email = (String) request.get("email");

    Response response = AcceptanceStack.shared().api().register(request);

    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    Mailpit mailpit = AcceptanceStack.mailpit();
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(() -> assertThat(mailpit.searchTo(email, email)).hasSize(1));
    Mailpit.Message message = mailpit.searchTo(email, email).get(0);
    assertThat(message.to()).containsExactly(email);
    assertThat(message.subject())
        .isEqualTo("Registration confirmed: " + AcceptanceStack.CONFERENCE_NAME);
    assertThat(message.text())
        .contains("Špela")
        .contains("Žagar")
        .contains("Delavnica: testiranje programske opreme");
    assertThat(message.attachments()).isEmpty();
    sleep(Duration.ofSeconds(2));
    assertThat(mailpit.searchTo(email, email)).hasSize(1);
  }

  @Test
  @DisplayName("AC-006-02 no email is sent to the participant of a rejected registration")
  void ac006_02_rejectedRegistrationSendsNoEmail() {
    Map<String, Object> request = with(external(), "consentIds", List.of());
    String email = (String) request.get("email");

    Response response = AcceptanceStack.shared().api().register(request);

    assertThat(response.status()).as(response.toString()).isEqualTo(400);
    sleep(Duration.ofSeconds(3));
    assertThat(AcceptanceStack.mailpit().search(email)).isEmpty();
  }

  @Test
  @DisplayName("AC-006-03 a failed participant email leaves the registration accepted")
  void ac006_03_emailFailureKeepsRegistration(CapturedOutput output) {
    Map<String, Object> request = external();
    request.put("lastName", "Unreachablemailovič");
    String email = (String) request.get("email");

    try (RunningApp app =
        AcceptanceStack.start(Map.of("SMTP_PORT", String.valueOf(AcceptanceStack.closedPort())))) {
      Response response = app.api().register(request);

      assertThat(response.status()).as(response.toString()).isEqualTo(201);
      String id = response.json().path("id").asString();
      assertThat(AcceptanceStack.db().registrationByEmail(email)).isPresent();
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () ->
                  assertThat(Us006ParticipantEmailAcceptanceTest.warningLines(output, id))
                      .isNotEmpty());
      sleep(Duration.ofSeconds(1));
    }
    assertThat(output.getAll()).doesNotContain(email).doesNotContain("Unreachablemailovič");
    assertThat(AcceptanceStack.db().registrationByEmail(email)).isPresent();
  }

  /** Log lines at WARN level that name the registration id. */
  static List<String> warningLines(CapturedOutput output, String id) {
    return output.getAll().lines().filter(l -> l.contains("WARN") && l.contains(id)).toList();
  }

  static void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
