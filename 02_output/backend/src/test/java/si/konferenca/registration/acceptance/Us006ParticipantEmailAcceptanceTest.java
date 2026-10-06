package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Payloads.external;
import static si.konferenca.registration.acceptance.support.Payloads.student;
import static si.konferenca.registration.acceptance.support.Payloads.with;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Json;
import tools.jackson.databind.JsonNode;

/** US-006 Participant email confirmation (`email-messages.json` participantConfirmation). */
class Us006ParticipantEmailAcceptanceTest extends AcceptanceTestBase {

  private static final Duration WAIT = Duration.ofSeconds(15);

  @Test
  void AC_006_01_participant_receives_a_confirmation_with_name_type_and_options() {
    Map<String, Object> body = external();
    String email = (String) body.get("email");
    assertAccepted(api.register(body));

    JsonNode message = mail.awaitMessageTo(email, WAIT);

    assertThat(Json.strings(message.path("To"), "Address")).containsExactly(email);
    assertThat(message.path("From").path("Address").asString())
        .isEqualTo(AcceptanceEnvironment.MAIL_FROM);
    assertThat(message.path("Subject").asString()).contains(AcceptanceEnvironment.CONFERENCE_NAME);
    String text = message.path("Text").asString();
    assertThat(text)
        .contains("Janez Novak")
        .contains(AcceptanceEnvironment.CONFERENCE_NAME)
        .contains("External participant")
        .contains("Workshop: AI in research")
        .contains("Lunch, day 1");
  }

  @Test
  void AC_006_01_student_confirmation_states_the_student_type() {
    Map<String, Object> body = student();
    assertAccepted(api.register(body));

    String text = mail.awaitMessageTo((String) body.get("email"), WAIT).path("Text").asString();

    assertThat(text).contains("Ana Horvat").contains("Student").contains("Workshop: Open data");
  }

  @Test
  void AC_006_02_slovenian_characters_are_unchanged_in_the_participant_email() {
    Map<String, Object> body = external();
    body.put("firstName", "Žiga");
    body.put("lastName", "Čebašek Šuštar");
    assertAccepted(api.register(body));

    JsonNode message = mail.awaitMessageTo((String) body.get("email"), WAIT);

    assertThat(message.path("Text").asString()).contains("Žiga Čebašek Šuštar");
  }

  @Test
  void AC_006_03_header_text_and_markup_cannot_add_recipients_or_render() {
    Map<String, Object> body = external();
    String email = (String) body.get("email");
    body.put("lastName", "Novak Bcc: intruder@example.com");
    body.put("organization", "<b>Inštitut</b> <script>alert(1)</script>");
    assertAccepted(api.register(body));

    JsonNode message = mail.awaitMessageTo(email, WAIT);

    assertThat(Json.strings(message.path("To"), "Address")).containsExactly(email);
    assertThat(message.path("Cc").size()).isZero();
    assertThat(message.path("Bcc").size()).isZero();
    assertThat(message.path("HTML").asString()).isEmpty();
    assertThat(message.path("Text").asString()).contains("Novak Bcc: intruder@example.com");
    JsonNode headers = mail.headers(message.path("ID").asString());
    assertThat(headers.has("Bcc")).isFalse();
    assertThat(headers.has("Cc")).isFalse();
    assertThat(Json.strings(headers.path("Content-Type")).get(0)).startsWith("text/plain");
    for (JsonNode summary : mail.messagesAfter(Duration.ofSeconds(1))) {
      for (String field : List.of("To", "Cc", "Bcc")) {
        assertThat(Json.strings(summary.path(field), "Address"))
            .doesNotContain("intruder@example.com");
      }
    }
  }

  @Test
  void AC_006_05_rejected_registration_sends_no_email() {
    Map<String, Object> body = with(external(), "consentIds", List.of());

    assertThat(api.register(body).status()).isEqualTo(400);

    assertThat(mail.messagesAfter(Duration.ofSeconds(3))).isEmpty();
  }
}
