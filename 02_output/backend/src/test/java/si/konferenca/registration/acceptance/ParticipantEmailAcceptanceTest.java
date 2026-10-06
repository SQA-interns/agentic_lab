package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.with;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.Mailpit;
import si.konferenca.registration.acceptance.support.RunningApp;
import si.konferenca.registration.acceptance.support.RunningApp.Response;
import tools.jackson.databind.JsonNode;

/** US-006 Participant email confirmation (emails.schema.json participantConfirmation). */
class ParticipantEmailAcceptanceTest {

  private static RunningApp app;
  private static final Mailpit MAIL = AcceptanceEnvironment.mailpit();

  @BeforeAll
  static void start() {
    app = RunningApp.start();
  }

  @AfterAll
  static void stop() {
    app.close();
  }

  @Test
  void AC_006_01_participantReceivesConfirmationWithNameAndOptions() {
    Map<String, Object> reg = external();
    reg.put("firstName", "Špela");
    reg.put("lastName", "Žagar");
    String email = (String) reg.get("email");

    Response r = app.register(reg);
    assertThat(r.status()).isEqualTo(201);

    JsonNode msg = MAIL.awaitMessageTo(email);
    assertThat(msg.path("Subject").asString())
        .isEqualTo("Registration received: " + AcceptanceEnvironment.CONFERENCE_NAME);
    assertThat(msg.path("From").path("Address").asString())
        .isEqualTo(AcceptanceEnvironment.MAIL_FROM);
    assertThat(msg.path("To")).hasSize(1);
    String text = msg.path("Text").asString();
    assertThat(text).contains("Špela").contains("Žagar").contains("AI workshop").contains("Lunch");
    assertThat(msg.path("HTML").asString()).isEmpty();
    assertThat(msg.path("Attachments")).isEmpty();
  }

  @Test
  void AC_006_02_rejectedRegistrationSendsNoEmail() {
    Map<String, Object> reg = with(external(), "consentGiven", false);
    String email = (String) reg.get("email");

    assertThat(app.register(reg).status()).isEqualTo(400);

    assertThat(MAIL.noMessageWithin(email, Duration.ofSeconds(3))).isTrue();
  }

  @Test
  void AC_006_03_unavailableMailServerDoesNotPreventAcceptance() throws Exception {
    int closedPort;
    try (ServerSocket s = new ServerSocket(0)) {
      closedPort = s.getLocalPort();
    }
    Map<String, String> config = AcceptanceEnvironment.defaultConfiguration();
    config.put("SMTP_HOST", "127.0.0.1");
    config.put("SMTP_PORT", String.valueOf(closedPort));
    config.put("spring.mail.host", "127.0.0.1");
    config.put("spring.mail.port", String.valueOf(closedPort));
    try (RunningApp noMail = RunningApp.start(config)) {
      Map<String, Object> reg = external();

      Response r = noMail.register(reg);

      assertThat(r.status()).isEqualTo(201);
      assertThat(r.json().path("registrationId").asString()).isNotBlank();
      assertThat(noMail.registrationRows((String) reg.get("email"))).hasSize(1);
    }
  }
}
