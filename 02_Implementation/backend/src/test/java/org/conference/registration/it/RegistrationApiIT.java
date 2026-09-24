package org.conference.registration.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Behavioural/contract tests for the registration API against real PostgreSQL and SMTP. */
class RegistrationApiIT extends AbstractIntegrationTest {

  private static final String EXTERNAL = "/api/registrations/external";
  private static final String STUDENT = "/api/registrations/student";

  // --- US-001 / US-002 happy paths ---------------------------------------------------------

  @Test
  void externalRegistrationIsAcceptedPersistedBackedUpAndMailed() throws Exception { // AC-001-01
    JsonNode body =
        json(
            postJson(EXTERNAL, externalPayload())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("EXTERNAL")));
    String id = body.get("id").asText();

    // AC-005-01: relational row with fixed fields and options
    Map<String, Object> row = jdbc.queryForMap("SELECT * FROM registration WHERE id = ?::uuid", id);
    assertThat(row.get("organization")).isEqualTo("Univerza v Mariboru");
    assertThat(row.get("student_id")).isNull();
    List<String> options =
        jdbc.queryForList(
            "SELECT option_id FROM registration_option WHERE registration_id = ?::uuid ORDER BY 1",
            String.class,
            id);
    assertThat(options).containsExactly("meal-lunch-day1", "ws-secure-coding");

    // AC-005-02 / AC-005-06: JSON backup exists and is sufficient for recovery
    Path backup = singleBackupFile();
    JsonNode backupJson = objectMapper.readTree(Files.readAllBytes(backup));
    assertThat(backupJson.get("id").asText()).isEqualTo(id);
    assertThat(backupJson.get("type").asText()).isEqualTo("EXTERNAL");
    assertThat(backupJson.at("/participant/email").asText()).isEqualTo("ana.novak@example.si");
    assertThat(backupJson.get("privacyConsent").asBoolean()).isTrue();
    assertThat(backupJson.get("options")).hasSize(2);
    assertThat(backupJson.hasNonNull("createdAt")).isTrue();

    // AC-006-01 / AC-007-01 / AC-007-02: participant + organizer mail with JSON attachment
    // (GreenMail stores one copy per recipient: participant + 2 organizers)
    List<MimeMessage> messages = awaitMessagesContaining(id, 3, 10_000);
    assertThat(messages.stream().filter(m -> recipients(m).contains("ana.novak@example.si")))
        .hasSize(1);
    MimeMessage organizerMail =
        messages.stream()
            .filter(m -> recipients(m).contains("organizers@conference.test"))
            .findFirst()
            .orElseThrow();
    assertThat(rawMessage(organizerMail))
        .contains("filename=registration-" + id + ".json")
        .contains("Content-Type: application/json");
  }

  @Test
  void studentRegistrationIsAccepted() throws Exception { // AC-002-01
    postJson(STUDENT, studentPayload())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("STUDENT"))
        .andExpect(jsonPath("$.id").isNotEmpty());
    Map<String, Object> row = jdbc.queryForMap("SELECT * FROM registration");
    assertThat(row.get("student_id")).isEqualTo("E1234567");
    assertThat(row.get("organization")).isNull();
    assertThat(backupFileCount()).isEqualTo(1);
  }

  @Test
  void slovenianCharactersArePreserved() throws Exception { // AC-001-07, AC-006-04
    Map<String, Object> payload = externalPayload();
    payload.put("firstName", "Žiga");
    payload.put("lastName", "Čeh-Šuštar");
    payload.put("organization", "Univerza v Mariboru – FERI, Šolska ulica");
    String id =
        json(postJson(EXTERNAL, payload).andExpect(status().isCreated())).get("id").asText();

    Map<String, Object> row = jdbc.queryForMap("SELECT * FROM registration");
    assertThat(row.get("first_name")).isEqualTo("Žiga");
    assertThat(row.get("last_name")).isEqualTo("Čeh-Šuštar");
    assertThat(row.get("organization")).isEqualTo("Univerza v Mariboru – FERI, Šolska ulica");
    assertThat(Files.readString(singleBackupFile(), StandardCharsets.UTF_8)).contains("Čeh-Šuštar");

    MimeMessage confirmation =
        awaitMessagesContaining(id, 3, 10_000).stream()
            .filter(m -> recipients(m).contains("ana.novak@example.si"))
            .findFirst()
            .orElseThrow();
    assertThat((String) confirmation.getContent()).contains("Žiga Čeh-Šuštar");
  }

  @Test
  void surroundingWhitespaceIsTrimmed() throws Exception { // AC-001-08
    Map<String, Object> payload = externalPayload();
    payload.put("firstName", "  Ana  ");
    payload.put("email", " ana.novak@example.si ");
    postJson(EXTERNAL, payload).andExpect(status().isCreated());
    Map<String, Object> row = jdbc.queryForMap("SELECT * FROM registration");
    assertThat(row.get("first_name")).isEqualTo("Ana");
    assertThat(row.get("email")).isEqualTo("ana.novak@example.si");
  }

  @Test
  void registrationWithoutOptionsIsAccepted() throws Exception { // AC-003-06
    Map<String, Object> payload = externalPayload();
    payload.remove("optionIds");
    postJson(EXTERNAL, payload).andExpect(status().isCreated());
  }

  @Test
  void markupInFreeTextIsStoredLiterally() throws Exception { // AC-PC-01
    Map<String, Object> payload = externalPayload();
    payload.put("organization", "<script>alert(1)</script>");
    postJson(EXTERNAL, payload).andExpect(status().isCreated());
    assertThat(jdbc.queryForObject("SELECT organization FROM registration", String.class))
        .isEqualTo("<script>alert(1)</script>");
  }

  // --- validation --------------------------------------------------------------------------

  @Test
  void malformedEmailIsRejectedAndNothingStored() throws Exception { // AC-001-04, AC-005-03
    Map<String, Object> payload = externalPayload();
    payload.put("email", "not-an-email");
    postJson(EXTERNAL, payload)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors.email").exists());
    assertThat(registrationCount()).isZero();
    assertThat(backupFileCount()).isZero();
  }

  @Test
  void emailWithoutTopLevelDomainIsRejected() throws Exception {
    Map<String, Object> payload = externalPayload();
    payload.put("email", "ana@localhost");
    postJson(EXTERNAL, payload)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.email").exists());
  }

  @Test
  void missingConsentIsRejected() throws Exception { // AC-001-05
    Map<String, Object> payload = externalPayload();
    payload.put("privacyConsent", false);
    postJson(EXTERNAL, payload)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.privacyConsent").exists());
    payload.remove("privacyConsent");
    postJson(EXTERNAL, payload)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.privacyConsent").exists());
    assertThat(registrationCount()).isZero();
  }

  @Test
  void whitespaceOnlyRequiredFieldIsRejected() throws Exception { // AC-001-09
    Map<String, Object> payload = externalPayload();
    payload.put("organization", "   ");
    postJson(EXTERNAL, payload)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.organization").value("This field is required."));
  }

  @Test
  void missingStudentIdIsRejected() throws Exception { // AC-002-03
    Map<String, Object> payload = studentPayload();
    payload.remove("studentId");
    postJson(STUDENT, payload)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.studentId").exists());
  }

  @Test
  void studentEndpointRejectsExternalFieldSet() throws Exception { // AC-002-05
    Map<String, Object> payload = externalPayload();
    postJson(STUDENT, payload)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    assertThat(registrationCount()).isZero();
  }

  @Test
  void optionNotAvailableToStudentsIsRejected() throws Exception { // AC-002-06
    Map<String, Object> payload = studentPayload();
    payload.put("optionIds", List.of("ev-gala-dinner"));
    postJson(STUDENT, payload)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.optionIds").exists());
  }

  @Test
  void unknownOptionIsRejected() throws Exception { // AC-003-03
    Map<String, Object> payload = externalPayload();
    payload.put("optionIds", List.of("ws-does-not-exist"));
    postJson(EXTERNAL, payload)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.optionIds").exists());
    assertThat(registrationCount()).isZero();
  }

  @Test
  void inactiveOptionIsRejected() throws Exception { // AC-003-04
    Map<String, Object> payload = externalPayload();
    payload.put("optionIds", List.of("ws-legacy"));
    postJson(EXTERNAL, payload).andExpect(status().isBadRequest());
    assertThat(registrationCount()).isZero();
  }

  @Test
  void overlongFieldIsRejected() throws Exception { // AC-PC-02
    Map<String, Object> payload = externalPayload();
    payload.put("organization", "x".repeat(201));
    postJson(EXTERNAL, payload)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.organization").exists());
  }

  @Test
  void controlCharactersAreRejected() throws Exception {
    Map<String, Object> payload = externalPayload();
    payload.put("organization", "FERI\r\nBcc: victim@example.com");
    postJson(EXTERNAL, payload).andExpect(status().isBadRequest());
  }

  @Test
  void invalidRegistrationSendsNoEmail() throws Exception { // AC-006-02, AC-007-03
    Map<String, Object> payload = externalPayload();
    payload.put("lastName", "Nomailmarker");
    payload.put("email", "bad");
    postJson(EXTERNAL, payload).andExpect(status().isBadRequest());
    assertThat(awaitMessagesContaining("Nomailmarker", 1, 1_500)).isEmpty();
  }

  // --- anti-automation ---------------------------------------------------------------------

  @Test
  void filledHoneypotIsRejectedAndNotStored() throws Exception { // AC-PC-03
    Map<String, Object> payload = externalPayload();
    payload.put("website", "http://spam.example");
    postJson(EXTERNAL, payload)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("SUBMISSION_REJECTED"));
    assertThat(registrationCount()).isZero();
  }

  @Test
  void forgedTokenIsRejected() throws Exception {
    Map<String, Object> payload = externalPayload();
    payload.put("formToken", "forged.token");
    postJson(EXTERNAL, payload)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("SUBMISSION_REJECTED"));
  }

  @Test
  void tokenCannotBeReplayed() throws Exception {
    Map<String, Object> payload = externalPayload();
    postJson(EXTERNAL, payload).andExpect(status().isCreated());
    postJson(EXTERNAL, payload)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("SUBMISSION_REJECTED"));
    assertThat(registrationCount()).isEqualTo(1);
  }

  // --- options endpoint (US-003) ----------------------------------------------------------

  @Test
  void optionsEndpointReturnsOnlyActiveOptionsForVariant()
      throws Exception { // AC-003-01/05, AC-002-07
    JsonNode external =
        json(mvc.perform(get("/api/options").param("type", "EXTERNAL")).andExpect(status().isOk()));
    JsonNode student =
        json(mvc.perform(get("/api/options").param("type", "STUDENT")).andExpect(status().isOk()));
    assertThat(ids(external))
        .contains("ev-gala-dinner")
        .doesNotContain("ws-legacy", "ev-career-fair");
    assertThat(ids(student))
        .contains("ev-career-fair")
        .doesNotContain("ws-legacy", "ev-gala-dinner");
    assertThat(external.get(0).has("category")).isTrue();
    assertThat(external.get(0).has("name")).isTrue();
  }

  @Test
  void invalidTypeParameterIsBadRequest() throws Exception {
    mvc.perform(get("/api/options").param("type", "ADMIN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
  }

  // --- error hygiene / headers (AC-PC-06, AC-PC-07, AC-PC-10) ------------------------------

  @Test
  void malformedJsonGivesGenericErrorWithoutInternals() throws Exception { // AC-PC-07
    String body =
        mvc.perform(
                post(EXTERNAL).contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain("Exception").doesNotContain("at org.");
  }

  @Test
  void unsupportedMediaTypeIsRejected() throws Exception {
    mvc.perform(post(EXTERNAL).contentType(MediaType.TEXT_PLAIN).content("x"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void oversizedBodyIsRejected() throws Exception {
    mvc.perform(
            post(EXTERNAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"" + "a".repeat(20_000) + "\"}"))
        .andExpect(status().isPayloadTooLarge());
  }

  @Test
  void responsesCarrySecurityHeaders() throws Exception { // AC-PC-06
    mvc.perform(get("/api/options").param("type", "EXTERNAL"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(
            header()
                .string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().exists("X-Request-Id"));
  }

  @Test
  void unknownEndpointsDoNotExposeData() throws Exception { // AC-PC-10
    mvc.perform(get("/api/registrations")).andExpect(status().is4xxClientError());
    mvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError());
    mvc.perform(get("/api/admin/registrations")).andExpect(status().isUnauthorized());
  }

  @Test
  void healthEndpointsAreUp() throws Exception { // AC-PC-09
    for (String path :
        new String[] {
          "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"
        }) {
      mvc.perform(get(path))
          .andExpect(status().isOk())
          .andExpect(content().json("{\"status\":\"UP\"}"));
    }
  }

  // --- helpers -----------------------------------------------------------------------------

  private static List<String> ids(JsonNode array) {
    return Stream.of(array)
        .flatMap(a -> java.util.stream.StreamSupport.stream(a.spliterator(), false))
        .map(n -> n.get("id").asText())
        .toList();
  }

  private Path singleBackupFile() throws Exception {
    try (Stream<Path> files = Files.list(BACKUP_DIR)) {
      List<Path> list = files.toList();
      assertThat(list).hasSize(1);
      return list.get(0);
    }
  }
}
