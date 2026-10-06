package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Payloads.external;
import static si.konferenca.registration.acceptance.support.Payloads.student;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceEnvironment;
import si.konferenca.registration.acceptance.support.AcceptanceTestBase;
import si.konferenca.registration.acceptance.support.Json;
import tools.jackson.databind.JsonNode;

/** US-007 Organizer notification (`email-messages.json` organizerNotification). */
class Us007OrganizerNotificationAcceptanceTest extends AcceptanceTestBase {

  private static final Duration WAIT = Duration.ofSeconds(15);
  private static final String FIRST_ORGANIZER = AcceptanceEnvironment.ORGANIZER_EMAILS.get(0);

  private JsonNode organizerMessage() {
    return mail.awaitMessageTo(FIRST_ORGANIZER, WAIT);
  }

  private static JsonNode onlyAttachment(JsonNode message) {
    assertThat(message.path("Attachments").size()).isEqualTo(1);
    return message.path("Attachments").get(0);
  }

  @Test
  void AC_007_01_organizers_receive_the_submitted_data_with_the_json_attached() {
    Map<String, Object> body = external();
    String id = assertAccepted(api.register(body)).path("id").asString();

    JsonNode message = organizerMessage();

    assertThat(Json.strings(message.path("To"), "Address"))
        .containsExactlyInAnyOrderElementsOf(AcceptanceEnvironment.ORGANIZER_EMAILS);
    assertThat(message.path("Text").asString())
        .contains("Janez")
        .contains("Novak")
        .contains((String) body.get("email"))
        .contains("Institute of Testing")
        .contains("External participant")
        .contains("Workshop: AI in research");
    JsonNode attachment = onlyAttachment(message);
    assertThat(attachment.path("FileName").asString()).isEqualTo("registration-" + id + ".json");
    assertThat(attachment.path("ContentType").asString()).isEqualTo("application/json");
  }

  @Test
  void AC_007_02_attachment_is_identical_to_the_stored_json_copy() throws IOException {
    assertAccepted(api.register(student()));

    JsonNode message = organizerMessage();
    byte[] attached =
        mail.part(message.path("ID").asString(), onlyAttachment(message).path("PartID").asString());

    assertThat(copies.copies()).hasSize(1);
    assertThat(attached).isEqualTo(Files.readAllBytes(copies.copies().get(0)));
  }

  @Test
  void AC_007_03_notification_contains_only_this_registrations_data() {
    Map<String, Object> earlier = external();
    assertAccepted(api.register(earlier));
    organizerMessage();
    mail.clear();
    Map<String, Object> body = student();
    String id = assertAccepted(api.register(body)).path("id").asString();

    JsonNode message = organizerMessage();

    String text = message.path("Text").asString();
    String copyName =
        copies.copies().stream()
            .map(p -> p.getFileName().toString())
            .filter(name -> name.contains(id))
            .findFirst()
            .orElseThrow();
    assertThat(text).contains((String) body.get("email"));
    for (String foreign :
        List.of(
            (String) earlier.get("email"),
            copyName,
            AcceptanceEnvironment.JSON_COPY_DIR.toString(),
            "email_normalized",
            "json_copy_file",
            AcceptanceEnvironment.ORGANIZER_PASSWORD,
            "Exception")) {
      assertThat(text).doesNotContain(foreign);
    }
  }

  @Test
  void AC_007_04_slovenian_characters_are_unchanged_in_the_notification_and_attachment() {
    Map<String, Object> body = student();
    body.put("firstName", "Špela");
    body.put("lastName", "Žagar");
    body.put("studyProgramme", "Računalništvo in informatika");
    assertAccepted(api.register(body));

    JsonNode message = organizerMessage();
    String attached =
        new String(
            mail.part(
                message.path("ID").asString(), onlyAttachment(message).path("PartID").asString()),
            StandardCharsets.UTF_8);

    assertThat(message.path("Text").asString())
        .contains("Špela")
        .contains("Žagar")
        .contains("Računalništvo in informatika");
    assertThat(Json.read(attached).path("participant").path("firstName").asString())
        .isEqualTo("Špela");
    assertThat(attached).contains("Računalništvo in informatika");
  }
}
