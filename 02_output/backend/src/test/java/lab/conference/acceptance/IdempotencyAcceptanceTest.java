package lab.conference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import lab.conference.acceptance.support.Api;
import lab.conference.acceptance.support.AppInstance;
import lab.conference.acceptance.support.Mailpit;
import lab.conference.acceptance.support.Payloads;
import lab.conference.acceptance.support.Store;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** US-004: client request ID retries and repeated emails (BR-06). */
class IdempotencyAcceptanceTest {

  private static AppInstance app;
  private static Api api;

  @BeforeAll
  static void start() {
    app = AppInstance.builder().build().start();
    api = app.api();
  }

  @AfterAll
  static void stop() {
    app.stop();
  }

  @Test
  void ac_004_03_retryWithSameRequestIdReturnsOriginalRegistration() {
    Map<String, Object> body = Payloads.external();
    String email = (String) body.get("email");

    Api.Response first = api.postExternal(body);
    Api.Response retry = api.postExternal(body);
    Api.Response thirdRetry = api.postExternal(body);

    assertThat(first.status()).as(first.toString()).isEqualTo(201);
    assertThat(retry.status()).as(retry.toString()).isIn(200, 201);
    String id = first.json().path("registrationId").asText();
    assertThat(retry.json().path("registrationId").asText()).isEqualTo(id);
    assertThat(thirdRetry.json().path("registrationId").asText()).isEqualTo(id);
    assertThat(app.store().registrationsWithEmail(email)).isEqualTo(1);
    assertThat(app.store().jsonFiles())
        .filteredOn(p -> p.getFileName().toString().startsWith(id))
        .hasSize(1);
    Mailpit.shared().awaitMessagesTo(email, 1, Duration.ofSeconds(30));
    Mailpit.sleep(3000);
    assertThat(Mailpit.shared().messagesTo(email)).hasSize(1);
  }

  @Test
  void ac_004_03_retryOfStudentRequestDoesNotDuplicate() {
    Map<String, Object> body = Payloads.student();
    Api.Response first = api.postStudent(body);
    Api.Response retry = api.postStudent(body);
    assertThat(first.status()).isEqualTo(201);
    assertThat(retry.json().path("registrationId").asText())
        .isEqualTo(first.json().path("registrationId").asText());
    assertThat(app.store().registrationsWithEmail((String) body.get("email"))).isEqualTo(1);
  }

  @Test
  void ac_004_04_sameRequestIdWithDifferentContentIsConflict() {
    Map<String, Object> body = Payloads.external();
    assertThat(api.postExternal(body).status()).isEqualTo(201);
    Store.Snapshot before = app.store().snapshot();

    Map<String, Object> changed = new java.util.LinkedHashMap<>(body);
    changed.put("firstName", "Different");
    Api.Response conflict = api.postExternal(changed);

    assertThat(conflict.status()).as(conflict.toString()).isEqualTo(409);
    Mailpit.sleep(1500);
    app.store().assertUnchangedSince(before);
  }

  @Test
  void ac_004_05_sameEmailWithDifferentRequestIdsCreatesSeparateRegistrations() {
    Map<String, Object> first = Payloads.external();
    Map<String, Object> second = new java.util.LinkedHashMap<>(first);
    second.put("clientRequestId", java.util.UUID.randomUUID().toString());

    Api.Response a = api.postExternal(first);
    Api.Response b = api.postExternal(second);

    assertThat(a.status()).isEqualTo(201);
    assertThat(b.status()).as(b.toString()).isEqualTo(201);
    assertThat(b.json().path("registrationId").asText())
        .isNotEqualTo(a.json().path("registrationId").asText());
    assertThat(app.store().registrationsWithEmail((String) first.get("email"))).isEqualTo(2);
  }
}
