package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Checks.assertFieldError;
import static si.konferenca.registration.acceptance.support.Checks.assertNothingStored;
import static si.konferenca.registration.acceptance.support.Payloads.external;
import static si.konferenca.registration.acceptance.support.Payloads.student;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.Mailpit;
import si.konferenca.registration.acceptance.support.Payloads;
import si.konferenca.registration.acceptance.support.TestEnvironment;

/** US-006 the participant receives a safe plain-text confirmation email. */
class ParticipantEmailAcceptanceTest extends AcceptanceTest {

  @Test
  @DisplayName("AC-006-01 the participant receives a UTF-8 text email with name and options")
  void ac006_01_sendsConfirmationEmail() {
    Map<String, Object> payload = student();
    payload.put("firstName", "Špela");
    payload.put("lastName", "Čebašek Žužek");
    String email = (String) payload.get("email");

    Api.Response response = api.register(payload);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    List<Mailpit.Message> messages = Mailpit.messagesTo(email, 1, Duration.ofSeconds(15));
    assertThat(messages).hasSize(1);
    Mailpit.Message m = messages.get(0);
    assertThat(m.to()).containsExactly(email);
    assertThat(m.header("Content-Type")).startsWith("text/plain").containsIgnoringCase("utf-8");
    assertThat(m.subject()).contains(TestEnvironment.CONFERENCE_NAME);
    assertThat(m.text())
        .contains(TestEnvironment.CONFERENCE_NAME)
        .contains("Špela")
        .contains("Čebašek Žužek")
        .contains(response.json().get("id").asString())
        .contains(Payloads.WORKSHOP_NAME)
        .contains(Payloads.EVENT_NAME)
        .contains(Payloads.MEAL_NAME)
        .contains(Payloads.OTHER_NAME);
  }

  @Test
  @DisplayName("AC-006-02 markup in the input appears literally in a text/plain email")
  void ac006_02_markupIsNotInterpreted() {
    Map<String, Object> payload = external();
    payload.put("firstName", "<b>Ana</b>");
    payload.put("organization", "<script>alert(1)</script> d.o.o.");
    String email = (String) payload.get("email");

    Api.Response response = api.register(payload);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    List<Mailpit.Message> messages = Mailpit.messagesTo(email, 1, Duration.ofSeconds(15));
    assertThat(messages).hasSize(1);
    Mailpit.Message m = messages.get(0);
    assertThat(m.header("Content-Type")).startsWith("text/plain");
    assertThat(m.html()).isEmpty();
    assertThat(m.text()).contains("<b>Ana</b>");
  }

  @ParameterizedTest(name = "AC-006-02 a line break in {0} is rejected")
  @CsvSource({"firstName", "lastName", "organization", "email"})
  @DisplayName("AC-006-02 CR or LF in any field is rejected and no email is sent")
  void ac006_02_rejectsLineBreaks(String field) {
    Map<String, Object> payload = external();
    String email = (String) payload.get("email");
    String injected =
        "email".equals(field)
            ? email + "\r\nBcc: victim@elsewhere.test"
            : "Value\r\nBcc: victim@elsewhere.test";
    payload.put(field, injected);

    Api.Response response = api.register(payload);

    assertFieldError(response, 400, field);
    assertNothingStored(email, jsonDir());
    assertThat(Mailpit.messagesTo(email, 1, Duration.ofSeconds(2))).isEmpty();
    assertThat(Mailpit.messagesTo("victim@elsewhere.test", 1, Duration.ofSeconds(1))).isEmpty();
  }

  @Test
  @DisplayName("AC-006-02 a lone LF in a text field is rejected")
  void ac006_02_rejectsLoneLineFeed() {
    Map<String, Object> payload = external();
    String email = (String) payload.get("email");
    payload.put("lastName", "Novak\nSubject: spoofed");

    Api.Response response = api.register(payload);

    assertFieldError(response, 400, "lastName");
    assertNothingStored(email, jsonDir());
  }
}
