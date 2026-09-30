package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static si.konferenca.registration.acceptance.support.Payloads.external;
import static si.konferenca.registration.acceptance.support.Payloads.student;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.Api;
import si.konferenca.registration.acceptance.support.CopySchema;
import si.konferenca.registration.acceptance.support.JsonCopies;
import si.konferenca.registration.acceptance.support.Mailpit;
import si.konferenca.registration.acceptance.support.TestEnvironment;
import tools.jackson.databind.JsonNode;

/** US-007 organizers receive the submitted data with the raw JSON attached. */
class OrganizerNotificationAcceptanceTest extends AcceptanceTest {

  private Mailpit.Message organizerMessageFor(String registrationId) {
    long end = System.nanoTime() + Duration.ofSeconds(15).toNanos();
    while (System.nanoTime() < end) {
      List<Mailpit.Message> found = Mailpit.search("subject:\"" + registrationId + "\"");
      if (!found.isEmpty()) {
        assertThat(found).hasSize(1);
        return found.get(0);
      }
      try {
        Thread.sleep(250);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    throw new AssertionError("no organizer email with id " + registrationId);
  }

  @Test
  @DisplayName("AC-007-01 every organizer gets the data with the JSON copy attached unchanged")
  void ac007_01_sendsOrganizerEmailWithJsonAttachment() {
    Map<String, Object> payload = external();
    payload.put("lastName", "Štrukelj");
    String email = (String) payload.get("email");

    Api.Response response = api.register(payload);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    String id = response.json().get("id").asString();
    Mailpit.Message m = organizerMessageFor(id);
    assertThat(m.to())
        .containsExactlyInAnyOrder(
            TestEnvironment.ORGANIZER_EMAIL_1, TestEnvironment.ORGANIZER_EMAIL_2);
    assertThat(m.text())
        .contains("Ana")
        .contains("Štrukelj")
        .contains(email)
        .contains("Institut Jožef Stefan")
        .contains("Delavnica umetne inteligence");
    List<JsonNode> attachments = m.attachments();
    assertThat(attachments).hasSize(1);
    JsonNode a = attachments.get(0);
    assertThat(a.get("FileName").asString()).isEqualTo("registration-" + id + ".json");
    assertThat(a.get("ContentType").asString()).startsWith("application/json");
    byte[] attached = m.part(a.get("PartID").asString());
    String copy = JsonCopies.read(JsonCopies.copyOf(jsonDir(), id));
    assertThat(new String(attached, java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(copy);
  }

  @Test
  @DisplayName("AC-007-02 the organizer email contains only registration data")
  void ac007_02_containsOnlyRegistrationData() {
    Map<String, Object> payload = student();

    Api.Response response = api.register(payload);

    assertThat(response.status()).as(response.text()).isEqualTo(201);
    String id = response.json().get("id").asString();
    Mailpit.Message m = organizerMessageFor(id);
    byte[] attached = m.part(m.attachments().get(0).get("PartID").asString());
    JsonNode doc = Api.JSON.readTree(attached);
    assertThat(CopySchema.violations(doc)).isEmpty();
    Set<String> names = new HashSet<>(doc.propertyNames());
    assertThat(CopySchema.allowedProperties()).containsAll(names);
    String everything = m.text() + new String(attached, java.nio.charset.StandardCharsets.UTF_8);
    assertThat(everything)
        .doesNotContain(TestEnvironment.TEST_MODE_TOKEN)
        .doesNotContainIgnoringCase("recaptcha")
        .doesNotContainIgnoringCase("password")
        .doesNotContain(TestEnvironment.ORGANIZER_PASSWORD);
  }
}
