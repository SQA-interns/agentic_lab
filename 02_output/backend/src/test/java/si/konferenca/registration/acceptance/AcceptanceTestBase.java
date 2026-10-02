package si.konferenca.registration.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;

/** Shared set-up, request data and checks of the acceptance tests. */
abstract class AcceptanceTestBase {

  static final String PASSING_CAPTCHA_TOKEN = "test-pass";
  static final List<String> EXTERNAL_FIELDS =
      List.of("firstName", "lastName", "email", "organization");
  static final List<String> STUDENT_FIELDS =
      List.of("firstName", "lastName", "email", "studyInstitution", "studyProgramme", "studentId");

  @BeforeEach
  void resetStack() {
    Stack.reset();
  }

  /** A valid external participant registration. */
  static Map<String, Object> external() {
    Map<String, Object> registration = new LinkedHashMap<>();
    registration.put("type", "EXTERNAL");
    registration.put("firstName", "Ana");
    registration.put("lastName", "Novak");
    registration.put("email", "ana.novak@example.org");
    registration.put("organization", "Podjetje Primer");
    registration.put("optionIds", new ArrayList<>(List.of("ws-testing", "meal-lunch-day1")));
    registration.put("consent", true);
    registration.put("captchaToken", PASSING_CAPTCHA_TOKEN);
    return registration;
  }

  /** A valid student registration. */
  static Map<String, Object> student() {
    Map<String, Object> registration = new LinkedHashMap<>();
    registration.put("type", "STUDENT");
    registration.put("firstName", "Luka");
    registration.put("lastName", "Kovač");
    registration.put("email", "luka.kovac@example.org");
    registration.put("studyInstitution", "Univerza v Ljubljani");
    registration.put("studyProgramme", "Računalništvo in informatika");
    registration.put("studentId", "63210001");
    registration.put("optionIds", new ArrayList<>(List.of("ev-opening", "other-city-tour")));
    registration.put("consent", true);
    registration.put("captchaToken", PASSING_CAPTCHA_TOKEN);
    return registration;
  }

  static Map<String, Object> with(Map<String, Object> registration, String field, Object value) {
    registration.put(field, value);
    return registration;
  }

  /** Checks a 201 answer and returns the registration id. */
  static UUID assertAccepted(Api.Reply reply) {
    assertThat(reply.status()).as("status of %s", reply.text()).isEqualTo(201);
    return UUID.fromString(reply.json().path("id").asString());
  }

  /** Checks a 400 answer that reports the given field with the given code. */
  static void assertRejected(Api.Reply reply, String field, String code) {
    assertThat(reply.status()).as("status of %s", reply.text()).isEqualTo(400);
    assertThat(reply.fieldErrors()).contains(field + ":" + code);
  }

  /** Nothing was stored in the database or as a JSON copy, and no email was sent. */
  static void assertNothingStoredOrSent() {
    assertThat(Stack.registrationCount()).as("registrations in the database").isZero();
    assertThat(Stack.jsonCopies(Stack.app())).as("JSON copies").isEmpty();
    assertThat(Mailbox.messagesAfterSettling()).as("emails").isEmpty();
  }

  static Map<String, Object> registrationRow(UUID id) {
    List<Map<String, Object>> rows = Stack.rows("SELECT * FROM registration WHERE id = ?", id);
    assertThat(rows).as("database rows of registration %s", id).hasSize(1);
    return rows.get(0);
  }

  static List<Map<String, Object>> optionRows(UUID id) {
    return Stack.rows(
        "SELECT * FROM registration_option WHERE registration_id = ? ORDER BY position", id);
  }
}
