package org.example.conference.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.example.conference.registration.service.Hashing;
import org.example.conference.support.IntegrationTestBase;
import org.example.conference.support.Payloads;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

/** US-001/US-002/US-003/US-005 API behaviour against real PostgreSQL + filesystem. */
class RegistrationApiIT extends IntegrationTestBase {

  private static final String EXTERNAL = "/api/registrations/external";
  private static final String STUDENT = "/api/registrations/student";

  private String accept(String path, Map<String, Object> body) throws Exception {
    MvcResult result =
        postJson(path, body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.emailStatus").value("PENDING"))
            .andExpect(jsonPath("$.replayed").value(false))
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .get("registrationId")
        .asText();
  }

  private void assertNothingStored() throws Exception {
    assertThat(registrationCount()).isZero();
    assertThat(publishedFileCount()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM email_outbox", Integer.class)).isZero();
  }

  /** AC-001-01, AC-001-02 (P-01). */
  @Test
  void externalRegistrationWithUnicodeAndNbspIsStoredConsistently() throws Exception {
    String id = accept(EXTERNAL, Payloads.external());

    Map<String, Object> row =
        jdbc.queryForMap("SELECT * FROM registration WHERE id = ?::uuid", id);
    assertThat(row.get("first_name")).isEqualTo("Špela");
    assertThat(row.get("last_name")).isEqualTo("Novak Čebašek");
    assertThat(row.get("organization")).isEqualTo("Inštitut za žabe");
    assertThat(row.get("participant_type")).isEqualTo("EXTERNAL");

    String file =
        Files.readString(
            BACKUP_DIR.resolve("registrations").resolve(id + ".json"), StandardCharsets.UTF_8);
    assertThat(file).isEqualTo(row.get("raw_json"));
    assertThat(Hashing.sha256(file)).isEqualTo(row.get("raw_json_sha256"));
    assertThat(file).contains("\"firstName\" : \"Špela\"").doesNotContain("\\u00A0");
    assertThat(objectMapper.readTree(file).at("/selections/workshops/0/id").asText())
        .isEqualTo("ws-data-science");

    List<String> kinds =
        jdbc.queryForList(
            "SELECT kind || ':' || status FROM email_outbox WHERE registration_id = ?::uuid"
                + " ORDER BY kind, recipient",
            String.class,
            id);
    assertThat(kinds)
        .containsExactly(
            "ORGANIZER_NOTIFICATION:PENDING",
            "ORGANIZER_NOTIFICATION:PENDING",
            "PARTICIPANT_CONFIRMATION:PENDING");
    assertThat(registrationCount()).isEqualTo(1);
  }

  /** AC-002-01, AC-002-03 (P-02 valid part). */
  @Test
  void studentRegistrationIsAccepted() throws Exception {
    Map<String, Object> body = Payloads.student();
    body.put("selections", Payloads.selections(List.of("ws-data-science"), List.of()));
    String id = accept(STUDENT, body);
    Map<String, Object> row =
        jdbc.queryForMap("SELECT * FROM registration WHERE id = ?::uuid", id);
    assertThat(row.get("participant_type")).isEqualTo("STUDENT");
    assertThat(row.get("study_programme")).isEqualTo("Računalništvo");
    assertThat(row.get("student_id")).isEqualTo("S-000123");
    assertThat(row.get("organization")).isNull();
    assertThat(publishedFileCount()).isEqualTo(1);
  }

  /** AC-002-02 (P-02): each required student field missing, blank or NBSP-only. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "firstName",
        "lastName",
        "email",
        "studyInstitution",
        "studyProgramme",
        "studentId"
      })
  void studentRequiredFieldsAreEnforced(String field) throws Exception {
    for (Object value : new Object[] {null, "", "   ", "   "}) {
      Map<String, Object> body = Payloads.student();
      if (value == null) {
        body.remove(field);
      } else {
        body.put(field, value);
      }
      postJson(STUDENT, body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.errors[0].field").value(field))
          .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }
    assertNothingStored();
  }

  /** AC-001-03 (P-03): each required external field. */
  @ParameterizedTest
  @ValueSource(strings = {"firstName", "lastName", "email", "organization"})
  void externalRequiredFieldsAreEnforced(String field) throws Exception {
    for (Object value : new Object[] {null, "", " "}) {
      Map<String, Object> body = Payloads.external();
      if (value == null) {
        body.remove(field);
      } else {
        body.put(field, value);
      }
      postJson(EXTERNAL, body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value(field));
    }
    assertNothingStored();
  }

  /** AC-001-04 (P-03). */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "a@b",
        "no-at.example.org",
        "a b@example.org",
        "spela@exa mple.org",
        "spela@@example.org",
        ".spela@example.org",
        "spela@example..org",
        "špela@example.org"
      })
  void malformedEmailIsRejectedWithoutEchoingValue(String email) throws Exception {
    Map<String, Object> body = Payloads.external();
    body.put("email", email);
    MvcResult result =
        postJson(EXTERNAL, body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].code").value("EMAIL_INVALID"))
            .andReturn();
    assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .doesNotContain(email);
    assertNothingStored();
  }

  /** AC-001-05 (P-03). */
  @Test
  void invalidCaptchaIsRejected() throws Exception {
    Map<String, Object> body = Payloads.external();
    body.put("captchaToken", "wrong-token");
    postJson(EXTERNAL, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CAPTCHA_INVALID"));
    body.remove("captchaToken");
    postJson(EXTERNAL, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("captchaToken"));
    assertNothingStored();
  }

  /** AC-003-02 (P-04): unknown, inactive, wrong-group and duplicate IDs sent directly to API. */
  @ParameterizedTest
  @ValueSource(
      strings = {"ws-does-not-exist", "ws-legacy-cobol", "meal-lunch-day1", "DUPLICATE"})
  void invalidSelectionsAreRejected(String workshopId) throws Exception {
    Map<String, Object> body = Payloads.external();
    List<String> workshops =
        "DUPLICATE".equals(workshopId)
            ? List.of("ws-data-science", "ws-data-science")
            : List.of(workshopId);
    body.put("selections", Payloads.selections(workshops, List.of()));
    postJson(EXTERNAL, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("OPTION_INVALID"));
    assertNothingStored();
  }

  /** AC-003-05 (P-06): synthetic required consent absent/false rejected, present accepted. */
  @Test
  void requiredConsentIsEnforced() throws Exception {
    Map<String, Object> body = Payloads.external();
    body.remove("consents");
    postJson(EXTERNAL, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    body.put("consents", Map.of(Payloads.CONSENT_ID, false));
    postJson(EXTERNAL, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    body.put("consents", Map.of(Payloads.CONSENT_ID, true, "invented-consent", true));
    postJson(EXTERNAL, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CONSENT_INVALID"));
    assertNothingStored();

    body.put("consents", Map.of(Payloads.CONSENT_ID, true));
    String id = accept(EXTERNAL, body);
    assertThat(
            jdbc.queryForObject(
                "SELECT consent_id FROM registration_consent WHERE registration_id = ?::uuid",
                String.class,
                id))
        .isEqualTo(Payloads.CONSENT_ID);
  }

  /** Fixed fields cannot be extended or mixed between forms. */
  @Test
  void unknownOrForeignPropertiesAreRejected() throws Exception {
    Map<String, Object> body = Payloads.external();
    body.put("studentId", "S-1");
    postJson(EXTERNAL, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    Map<String, Object> student = Payloads.student();
    student.put("isAdmin", true);
    postJson(STUDENT, student).andExpect(status().isBadRequest());
    assertNothingStored();
  }

  @Test
  void controlCharactersAreRejected() throws Exception {
    Map<String, Object> body = Payloads.external();
    body.put("lastName", "Novak\r\nBcc: victim@example.org");
    postJson(EXTERNAL, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("FIELD_INVALID"));
    assertNothingStored();
  }

  @Test
  void tooLongValuesAreRejected() throws Exception {
    Map<String, Object> body = Payloads.external();
    body.put("firstName", "Š".repeat(101));
    postJson(EXTERNAL, body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));
    assertNothingStored();
  }

  /** AC-005-02 (P-07): replay returns the original registration, no duplicates. */
  @Test
  void repeatedRequestIdIsIdempotent() throws Exception {
    Map<String, Object> body = Payloads.external();
    String id = accept(EXTERNAL, body);
    body.put("captchaToken", "already-used-token");
    postJson(EXTERNAL, body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registrationId").value(id))
        .andExpect(jsonPath("$.replayed").value(true));
    assertThat(registrationCount()).isEqualTo(1);
    assertThat(publishedFileCount()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM email_outbox", Integer.class))
        .isEqualTo(3);

    body.put("organization", "Different organization");
    postJson(EXTERNAL, body)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REQUEST_ID_CONFLICT"));
    assertThat(registrationCount()).isEqualTo(1);
  }

  /** AC-005-02: concurrent duplicates yield exactly one registration. */
  @Test
  void concurrentDuplicateRequestsCreateOneRegistration() throws Exception {
    Map<String, Object> body = Payloads.student();
    byte[] json = objectMapper.writeValueAsBytes(body);
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Callable<MvcResult>> calls = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        calls.add(
            () ->
                mockMvc
                    .perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                STUDENT)
                            .contentType("application/json")
                            .content(json))
                    .andReturn());
      }
      Set<String> ids = new java.util.HashSet<>();
      Map<Integer, Integer> statuses = new HashMap<>();
      for (Future<MvcResult> future : pool.invokeAll(calls, 60, TimeUnit.SECONDS)) {
        MvcResult result = future.get();
        statuses.merge(result.getResponse().getStatus(), 1, Integer::sum);
        ids.add(
            objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("registrationId")
                .asText());
      }
      assertThat(statuses.get(201)).isEqualTo(1);
      assertThat(statuses.get(200)).isEqualTo(7);
      assertThat(ids).hasSize(1);
    } finally {
      pool.shutdownNow();
    }
    assertThat(registrationCount()).isEqualTo(1);
    assertThat(publishedFileCount()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM email_outbox", Integer.class))
        .isEqualTo(3);
  }

  @Test
  void invalidRequestIdIsRejected() throws Exception {
    Map<String, Object> body = Payloads.external();
    body.put("clientRequestId", "not-a-uuid");
    postJson(EXTERNAL, body).andExpect(status().isBadRequest());
    body.put("clientRequestId", UUID.randomUUID().toString());
    body.put("selections", Map.of("workshops", "ws-data-science"));
    postJson(EXTERNAL, body).andExpect(status().isBadRequest());
    assertNothingStored();
  }
}
