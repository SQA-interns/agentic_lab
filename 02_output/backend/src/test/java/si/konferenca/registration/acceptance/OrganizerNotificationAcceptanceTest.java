package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Registrations.external;
import static si.konferenca.registration.acceptance.support.Registrations.student;
import static si.konferenca.registration.acceptance.support.Registrations.with;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.Mailpit;
import si.konferenca.registration.acceptance.support.RunningApp;
import si.konferenca.registration.acceptance.support.RunningApp.Response;
import tools.jackson.databind.JsonNode;

/** US-007 Organizer notification (emails.schema.json organizerNotification). */
class OrganizerNotificationAcceptanceTest {

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

  /** The organizer message whose attachment belongs to the registration id. */
  private static JsonNode awaitOrganizerMessage(String organizer, String registrationId) {
    String attachmentName = "registration-" + registrationId + ".json";
    JsonNode[] found = new JsonNode[1];
    Awaitility.await()
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(250))
        .until(
            () -> {
              for (JsonNode summary : MAIL.messagesTo(organizer)) {
                JsonNode m = MAIL.message(summary.path("ID").asString());
                for (JsonNode a : m.path("Attachments")) {
                  if (a.path("FileName").asString().equals(attachmentName)) {
                    found[0] = m;
                    return true;
                  }
                }
              }
              return false;
            });
    return found[0];
  }

  private static Path jsonCopy(String registrationId) throws Exception {
    try (Stream<Path> s = Files.list(app.jsonCopyDir())) {
      return s.filter(p -> p.getFileName().toString().endsWith("_" + registrationId + ".json"))
          .findFirst()
          .orElseThrow();
    }
  }

  @Test
  void AC_007_01_everyOrganizerReceivesDataWithJsonIdenticalToCopy() throws Exception {
    Map<String, Object> reg = student();
    reg.put("firstName", "Tjaša");
    Response r = app.register(reg);
    assertThat(r.status()).isEqualTo(201);
    String id = r.json().path("registrationId").asString();
    byte[] copy = Files.readAllBytes(jsonCopy(id));

    for (String organizer : app.organizerEmails().split(",")) {
      JsonNode msg = awaitOrganizerMessage(organizer, id);
      assertThat(msg.path("Subject").asString())
          .isEqualTo("New student registration: " + AcceptanceEnvironment.CONFERENCE_NAME);
      String text = msg.path("Text").asString();
      assertThat(text)
          .contains("Tjaša")
          .contains("Kranjc")
          .contains((String) reg.get("email"))
          .contains("Univerza v Mariboru")
          .contains("Informatika")
          .contains("93120001")
          .contains("Security workshop")
          .contains("City tour");
      JsonNode attachment = msg.path("Attachments").get(0);
      assertThat(msg.path("Attachments")).hasSize(1);
      assertThat(attachment.path("ContentType").asString()).startsWith("application/json");
      byte[] attached =
          MAIL.attachment(msg.path("ID").asString(), attachment.path("PartID").asString());
      assertThat(attached).isEqualTo(copy);
    }
  }

  @Test
  void AC_007_02_rejectedRegistrationSendsNoOrganizerEmail() {
    Map<String, Object> reg = with(external(), "optionIds", java.util.List.of("unknown-x"));
    String marker = (String) reg.get("email");

    assertThat(app.register(reg).status()).isEqualTo(400);

    try {
      Thread.sleep(3000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    for (String organizer : app.organizerEmails().split(",")) {
      for (JsonNode summary : MAIL.messagesTo(organizer)) {
        assertThat(MAIL.message(summary.path("ID").asString()).path("Text").asString())
            .doesNotContain(marker);
      }
    }
  }

  @Test
  void AC_007_03_markupCharactersAppearAsText() {
    Map<String, Object> reg = external();
    String markup = "<b>ACME & \"Partners\"</b> <script>alert('x')</script>";
    reg.put("organization", markup);
    Response r = app.register(reg);
    assertThat(r.status()).isEqualTo(201);
    String id = r.json().path("registrationId").asString();

    JsonNode msg = awaitOrganizerMessage(app.organizerEmails().split(",")[0], id);

    assertThat(msg.path("HTML").asString()).isEmpty();
    assertThat(msg.path("Text").asString()).contains(markup);
  }
}
