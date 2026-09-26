package si.konferenca.registration.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import si.konferenca.registration.IntegrationTestBase;
import si.konferenca.registration.RegistrationJson;

class RegistrationApiIntegrationTest extends IntegrationTestBase {

  private ResultActions submit(String json) throws Exception {
    return mvc.perform(
        post("/api/registrations")
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8)
            .content(json));
  }

  private void assertNothingStoredOrSent() throws Exception {
    assertThat(registrationCount()).isZero();
    assertThat(backupFiles()).isEmpty();
    verify(mailSender, never()).send(any(MimeMessage.class));
  }

  @Test
  void conferenceEndpointOffersOnlyActiveOptionsAndConsents() throws Exception {
    mvc.perform(get("/api/conference"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.options[*].id", Matchers.hasItem("ws-testing")))
        .andExpect(jsonPath("$.options[*].id", Matchers.not(Matchers.hasItem("ws-archived"))))
        .andExpect(
            jsonPath(
                "$.options[*].category", Matchers.hasItems("WORKSHOP", "EVENT", "MEAL", "OTHER")))
        .andExpect(jsonPath("$.consents[0].id").value("privacy"))
        .andExpect(jsonPath("$.consents[0].required").value(true))
        .andExpect(jsonPath("$.recaptcha.testMode").value(true));
  }

  @Test
  void externalRegistrationIsStoredBackedUpAndConfirmedByEmail() throws Exception {
    MvcResult result =
        submit(RegistrationJson.external().with("firstName", "  Žiga  ").build())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.registrationId").isNotEmpty())
            .andExpect(jsonPath("$.type").value("EXTERNAL"))
            .andReturn();
    String id =
        com.jayway.jsonpath.JsonPath.read(
            result.getResponse().getContentAsString(), "$.registrationId");

    Map<String, Object> row = jdbc.queryForMap("SELECT * FROM registration WHERE id = ?::uuid", id);
    assertThat(row)
        .containsEntry("registration_type", "EXTERNAL")
        .containsEntry("first_name", "Žiga")
        .containsEntry("last_name", "Šušteršič")
        .containsEntry("organization", "Univerza v Ljubljani — FRI");
    assertThat(row.get("student_id")).isNull();
    assertThat(
            jdbc.queryForList(
                "SELECT option_id FROM registration_option WHERE registration_id = ?::uuid"
                    + " ORDER BY option_id",
                String.class,
                id))
        .containsExactly("meal-lunch", "ws-testing");
    assertThat(
            jdbc.queryForList(
                "SELECT consent_id FROM registration_consent WHERE registration_id = ?::uuid",
                String.class,
                id))
        .containsExactly("privacy");

    List<Path> backups = backupFiles();
    assertThat(backups).hasSize(1);
    String json = Files.readString(backups.getFirst(), StandardCharsets.UTF_8);
    assertThat(json)
        .contains("\"id\" : \"" + id + "\"")
        .contains("\"firstName\" : \"Žiga\"")
        .contains("\"lastName\" : \"Šušteršič\"")
        .contains("\"ws-testing\"")
        .doesNotContain("test-mode-token");

    ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender, times(2)).send(sent.capture());
    MimeMessage participant = sent.getAllValues().get(0);
    MimeMessage organizer = sent.getAllValues().get(1);
    assertThat(participant.getRecipients(Message.RecipientType.TO)[0].toString())
        .isEqualTo("ziga@example.si");
    assertThat(organizer.getRecipients(Message.RecipientType.TO)).hasSize(2);
    assertThat(organizer.getSubject()).contains("External participant");
  }

  @Test
  void studentRegistrationIsAccepted() throws Exception {
    submit(RegistrationJson.student().build())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("STUDENT"));
    Map<String, Object> row = jdbc.queryForMap("SELECT * FROM registration");
    assertThat(row)
        .containsEntry("registration_type", "STUDENT")
        .containsEntry("study_programme", "Računalništvo in informatika")
        .containsEntry("student_id", "63210001");
    assertThat(row.get("organization")).isNull();
    assertThat(backupFiles()).hasSize(1);
  }

  @Test
  void registrationWithoutOptionsIsAccepted() throws Exception {
    submit(RegistrationJson.external().with("optionIds", List.of()).build())
        .andExpect(status().isCreated());
    submit(RegistrationJson.external().without("optionIds").build())
        .andExpect(status().isCreated());
    assertThat(registrationCount()).isEqualTo(2);
  }

  @Test
  void missingRequiredFieldsAreRejected() throws Exception {
    submit(RegistrationJson.external().with("firstName", "   ").without("organization").build())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("firstName"))
        .andExpect(jsonPath("$.fieldErrors[0].message").value("This field is required."))
        .andExpect(jsonPath("$.fieldErrors[1].field").value("organization"));
    assertNothingStoredOrSent();
  }

  @Test
  void missingStudentFieldsAreRejected() throws Exception {
    submit(RegistrationJson.student().without("studentId").with("studyProgramme", "").build())
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath(
                "$.fieldErrors[*].field",
                Matchers.containsInAnyOrder("studyProgramme", "studentId")));
    assertNothingStoredOrSent();
  }

  @Test
  void invalidEmailIsRejected() throws Exception {
    submit(RegistrationJson.external().with("email", "not-an-email").build())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
        .andExpect(jsonPath("$.fieldErrors[0].message").value("Enter a valid email address."));
    assertNothingStoredOrSent();
  }

  @Test
  void unknownOptionIsRejected() throws Exception {
    submit(RegistrationJson.external().with("optionIds", List.of("does-not-exist")).build())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("optionIds"));
    assertNothingStoredOrSent();
  }

  @Test
  void inactiveOptionIsRejected() throws Exception {
    submit(RegistrationJson.student().with("optionIds", List.of("ws-archived")).build())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("optionIds"));
    assertNothingStoredOrSent();
  }

  @Test
  void missingMandatoryConsentIsRejected() throws Exception {
    submit(RegistrationJson.external().with("consentIds", List.of()).build())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("consents.privacy"));
    submit(RegistrationJson.student().without("consentIds").build())
        .andExpect(status().isBadRequest());
    assertNothingStoredOrSent();
  }

  @Test
  void failedCaptchaIsRejected() throws Exception {
    submit(RegistrationJson.external().with("recaptchaToken", "wrong").build())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CAPTCHA_FAILED"));
    submit(RegistrationJson.external().without("recaptchaToken").build())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CAPTCHA_FAILED"));
    assertNothingStoredOrSent();
  }

  @Test
  void malformedAndUnknownInputIsRejected() throws Exception {
    submit("{not json")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    submit(RegistrationJson.external().with("isAdmin", true).build())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    submit(RegistrationJson.external().with("type", "VIP").build())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    submit(RegistrationJson.external().without("type").build())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("type"));
    assertNothingStoredOrSent();
  }

  @Test
  void maliciousLookingInputIsStoredVerbatimAndSafely() throws Exception {
    String payload = "Robert'); DROP TABLE registration;-- <script>alert(1)</script>";
    submit(RegistrationJson.external().with("organization", payload).build())
        .andExpect(status().isCreated());
    assertThat(jdbc.queryForObject("SELECT organization FROM registration", String.class))
        .isEqualTo(payload);
  }

  @Test
  void wrongContentTypeIsRejected() throws Exception {
    mvc.perform(
            post("/api/registrations")
                .contentType(MediaType.TEXT_PLAIN)
                .content(RegistrationJson.external().build()))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    assertNothingStoredOrSent();
  }

  @Test
  void oversizedRequestIsRejected() throws Exception {
    submit(RegistrationJson.external().with("organization", "x".repeat(20_000)).build())
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    assertNothingStoredOrSent();
  }

  @Test
  void errorResponsesDoNotLeakInternals() throws Exception {
    String body =
        submit("{\"type\": [1,2,3]}")
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain("Exception").doesNotContain("at si.").doesNotContain("jackson");
  }

  @Test
  void securityHeadersArePresent() throws Exception {
    mvc.perform(get("/api/conference"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(
            header()
                .string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
  }

  @Test
  void unknownEndpointsAreDenied() throws Exception {
    mvc.perform(get("/api/registrations")).andExpect(status().is4xxClientError());
    mvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError());
  }

  @Test
  void healthEndpointsAreAvailable() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
  }
}
