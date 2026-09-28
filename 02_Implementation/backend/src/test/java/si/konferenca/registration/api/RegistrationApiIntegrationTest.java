package si.konferenca.registration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Address;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationRepository;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.support.PostgresIntegrationTest;
import si.konferenca.registration.support.TestData;

/**
 * API, integration and acceptance tests of the registration flow against PostgreSQL with the
 * complete Spring context (captcha test mode, mail sender mocked at the SMTP boundary).
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationApiIntegrationTest extends PostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RegistrationRepository repository;

  @MockitoBean private JavaMailSender mailSender;

  @BeforeEach
  void setUp() throws Exception {
    repository.deleteAll();
    try (Stream<Path> files = Files.list(BACKUP_DIR)) {
      for (Path file : files.toList()) {
        Files.delete(file);
      }
    }
    when(mailSender.createMimeMessage())
        .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
  }

  private MvcResult postExternal(Map<String, Object> body) throws Exception {
    return mockMvc
        .perform(
            post("/api/registrations/external")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)))
        .andReturn();
  }

  private MvcResult postStudent(Map<String, Object> body) throws Exception {
    return mockMvc
        .perform(
            post("/api/registrations/student")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)))
        .andReturn();
  }

  private JsonNode json(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsByteArray());
  }

  private List<MimeMessage> sentMessages() {
    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender, atLeast(0)).send(captor.capture());
    return captor.getAllValues();
  }

  private static List<String> recipients(MimeMessage message) throws Exception {
    Address[] to = message.getAllRecipients();
    return Arrays.stream(to).map(a -> ((InternetAddress) a).getAddress()).toList();
  }

  private static String textOf(Part part) throws Exception {
    Object content = part.getContent();
    if (content instanceof String text) {
      return text;
    }
    Multipart multipart = (Multipart) content;
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < multipart.getCount(); i++) {
      Part child = multipart.getBodyPart(i);
      if (child.getFileName() == null) {
        text.append(textOf(child));
      }
    }
    return text.toString();
  }

  private static Part attachment(MimeMessage message) throws Exception {
    Multipart multipart = (Multipart) message.getContent();
    List<Part> parts = new ArrayList<>();
    collectAttachments(multipart, parts);
    assertThat(parts).hasSize(1);
    return parts.get(0);
  }

  private static void collectAttachments(Multipart multipart, List<Part> parts) throws Exception {
    for (int i = 0; i < multipart.getCount(); i++) {
      Part part = multipart.getBodyPart(i);
      if (part.getFileName() != null) {
        parts.add(part);
      } else if (part.getContent() instanceof Multipart nested) {
        collectAttachments(nested, parts);
      }
    }
  }

  private static void assertFieldError(JsonNode body, String field, String code) {
    assertThat(body.get("error").asText()).isEqualTo("VALIDATION_FAILED");
    List<String> matches = new ArrayList<>();
    body.get("fieldErrors")
        .forEach(
            e -> {
              if (e.get("field").asText().equals(field)) {
                matches.add(e.get("code").asText());
              }
            });
    assertThat(matches).as("field errors for %s", field).contains(code);
  }

  private void assertNothingStored() throws Exception {
    assertThat(repository.count()).isZero();
    try (Stream<Path> files = Files.list(BACKUP_DIR)) {
      assertThat(files.toList()).isEmpty();
    }
    verify(mailSender, never()).send(any(MimeMessage.class));
  }

  @Nested
  class ExternalRegistration {

    @Test
    void acceptsValidRegistrationAndStoresIt() throws Exception { // AC-001-01, AC-005-01
      MvcResult result = postExternal(TestData.external());

      assertThat(result.getResponse().getStatus()).isEqualTo(201);
      JsonNode body = json(result);
      assertThat(body.get("registrationType").asText()).isEqualTo("EXTERNAL");
      UUID id = UUID.fromString(body.get("registrationId").asText());

      Registration stored = repository.findById(id).orElseThrow();
      assertThat(stored.getType()).isEqualTo(RegistrationType.EXTERNAL);
      assertThat(stored.getFirstName()).isEqualTo("Ana");
      assertThat(stored.getLastName()).isEqualTo("Novak");
      assertThat(stored.getEmail()).isEqualTo("ana.novak@example.si");
      assertThat(stored.getOrganization()).isEqualTo("Institut Jožef Stefan");
      assertThat(stored.isPrivacyConsent()).isTrue();
      assertThat(stored.getOptions())
          .extracting(o -> o.getOptionId())
          .containsExactlyInAnyOrder("ws-ai-practice", "meal-lunch-day1"); // AC-001-06
    }

    @Test
    void acceptsRegistrationWithoutOptions() throws Exception { // AC-001-07
      Map<String, Object> body = TestData.external();
      body.remove("optionIds");

      MvcResult result = postExternal(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(201);
      UUID id = UUID.fromString(json(result).get("registrationId").asText());
      assertThat(repository.findById(id).orElseThrow().getOptions()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"firstName", "lastName", "email", "organization"})
    void rejectsMissingRequiredField(String field) throws Exception { // AC-001-03
      Map<String, Object> body = TestData.external();
      body.remove(field);

      MvcResult result = postExternal(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertFieldError(json(result), field, "REQUIRED");
      assertNothingStored(); // AC-005-03, AC-006-02, AC-007-02
    }

    @ParameterizedTest
    @ValueSource(strings = {"firstName", "lastName", "email", "organization"})
    void rejectsWhitespaceOnlyRequiredField(String field) throws Exception { // AC-001-04
      Map<String, Object> body = TestData.external();
      body.put(field, "   \t ");

      MvcResult result = postExternal(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertFieldError(json(result), field, "REQUIRED");
      assertNothingStored();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana.novak", "ana@", "@example.com", "ana@example", "ana novak@x.si"})
    void rejectsInvalidEmail(String email) throws Exception { // AC-001-05
      Map<String, Object> body = TestData.external();
      body.put("email", email);

      MvcResult result = postExternal(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertFieldError(json(result), "email", "INVALID_EMAIL");
      assertNothingStored();
    }

    @Test
    void rejectsMissingMandatoryConsent() throws Exception { // AC-001-08
      Map<String, Object> body = TestData.external();
      body.put("consents", Map.of("privacy", false));

      MvcResult result = postExternal(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertFieldError(json(result), "consents.privacy", "CONSENT_REQUIRED");
      assertNothingStored();
    }

    @Test
    void rejectsAbsentConsentObject() throws Exception { // AC-001-08
      Map<String, Object> body = TestData.external();
      body.remove("consents");

      MvcResult result = postExternal(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertFieldError(json(result), "consents.privacy", "CONSENT_REQUIRED");
      assertNothingStored();
    }

    @Test
    void trimsLeadingAndTrailingWhitespace() throws Exception { // AC-001-09
      Map<String, Object> body = TestData.external();
      body.put("firstName", "  Ana  ");
      body.put("email", " ana.novak@example.si ");
      body.put("organization", " Institut Jožef Stefan ");

      MvcResult result = postExternal(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(201);
      Registration stored =
          repository
              .findById(UUID.fromString(json(result).get("registrationId").asText()))
              .orElseThrow();
      assertThat(stored.getFirstName()).isEqualTo("Ana");
      assertThat(stored.getEmail()).isEqualTo("ana.novak@example.si");
      assertThat(stored.getOrganization()).isEqualTo("Institut Jožef Stefan");
    }

    @Test
    void preservesUnicodeIncludingSlovenianCharacters() throws Exception { // AC-001-10
      Map<String, Object> body = TestData.external();
      body.put("firstName", "Špela Žiga");
      body.put("lastName", "Čeh-Šuštar");
      body.put("organization", "Univerza v Ljubljani – FRI ČŠŽ čšž");

      MvcResult result = postExternal(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(201);
      Registration stored =
          repository
              .findById(UUID.fromString(json(result).get("registrationId").asText()))
              .orElseThrow();
      assertThat(stored.getFirstName()).isEqualTo("Špela Žiga");
      assertThat(stored.getLastName()).isEqualTo("Čeh-Šuštar");
      assertThat(stored.getOrganization()).isEqualTo("Univerza v Ljubljani – FRI ČŠŽ čšž");
    }

    @Test
    void rejectsControlCharacters() throws Exception { // spec §7.1
      Map<String, Object> body = TestData.external();
      body.put("lastName", "Novak\u0000");

      MvcResult result = postExternal(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertFieldError(json(result), "lastName", "INVALID_CHARACTERS");
      assertNothingStored();
    }

    @Test
    void rejectsTooLongValue() throws Exception { // spec §3.1
      Map<String, Object> body = TestData.external();
      body.put("firstName", "A".repeat(101));

      MvcResult result = postExternal(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertFieldError(json(result), "firstName", "TOO_LONG");
    }

    @Test
    void acceptsMaximumLengthValue() throws Exception { // boundary spec §3.1
      Map<String, Object> body = TestData.external();
      body.put("firstName", "Ž".repeat(100));

      assertThat(postExternal(body).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void rejectsStudentFieldsOnExternalForm() throws Exception { // spec §7.1 mass assignment
      Map<String, Object> body = TestData.external();
      body.put("studentId", "123");

      MvcResult result = postExternal(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertThat(json(result).get("error").asText()).isEqualTo("MALFORMED_REQUEST");
      assertNothingStored();
    }
  }

  @Nested
  class StudentRegistration {

    @Test
    void acceptsValidStudentRegistration() throws Exception { // AC-002-01, AC-002-05
      MvcResult result = postStudent(TestData.student());

      assertThat(result.getResponse().getStatus()).isEqualTo(201);
      JsonNode body = json(result);
      assertThat(body.get("registrationType").asText()).isEqualTo("STUDENT");
      Registration stored =
          repository.findById(UUID.fromString(body.get("registrationId").asText())).orElseThrow();
      assertThat(stored.getType()).isEqualTo(RegistrationType.STUDENT);
      assertThat(stored.getStudyInstitution()).isEqualTo("Univerza v Ljubljani");
      assertThat(stored.getStudyProgramme()).isEqualTo("Računalništvo in informatika");
      assertThat(stored.getStudentId()).isEqualTo("63210001");
      assertThat(stored.getOrganization()).isNull();
      assertThat(stored.getOptions())
          .extracting(o -> o.getOptionId())
          .containsExactly("ev-conference-dinner");
    }

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
    void rejectsMissingOrBlankRequiredField(String field) throws Exception { // AC-002-03
      Map<String, Object> missing = TestData.student();
      missing.remove(field);
      MvcResult missingResult = postStudent(missing);
      assertThat(missingResult.getResponse().getStatus()).isEqualTo(400);
      assertFieldError(json(missingResult), field, "REQUIRED");

      Map<String, Object> blank = TestData.student();
      blank.put(field, "  ");
      MvcResult blankResult = postStudent(blank);
      assertThat(blankResult.getResponse().getStatus()).isEqualTo(400);
      assertFieldError(json(blankResult), field, "REQUIRED");
      assertNothingStored();
    }

    @Test
    void rejectsInvalidEmail() throws Exception { // AC-002-04
      Map<String, Object> body = TestData.student();
      body.put("email", "ziga.ceh@");

      MvcResult result = postStudent(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertFieldError(json(result), "email", "INVALID_EMAIL");
      assertNothingStored();
    }

    @Test
    void rejectsMissingMandatoryConsent() throws Exception { // AC-002-06
      Map<String, Object> body = TestData.student();
      body.put("consents", Map.of());

      MvcResult result = postStudent(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertFieldError(json(result), "consents.privacy", "CONSENT_REQUIRED");
      assertNothingStored();
    }

    @Test
    void acceptsAnyNonEmptyStudentIdWithoutExternalVerification() throws Exception { // AC-002-07
      Map<String, Object> body = TestData.student();
      body.put("studentId", "not-a-real-id-ÄŽ");

      assertThat(postStudent(body).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void rejectsOrganizationFieldOnStudentForm() throws Exception { // spec §7.1
      Map<String, Object> body = TestData.student();
      body.put("organization", "Company");

      MvcResult result = postStudent(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertThat(json(result).get("error").asText()).isEqualTo("MALFORMED_REQUEST");
    }
  }

  @Nested
  class OptionValidation {

    @Test
    void rejectsUnknownOption() throws Exception { // AC-002-08
      Map<String, Object> body = TestData.external();
      body.put("optionIds", List.of("ws-ai-practice", "does-not-exist"));

      MvcResult result = postExternal(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertFieldError(json(result), "optionIds", "UNKNOWN_OPTION");
      assertNothingStored();
    }

    @Test
    void rejectsInactiveOption() throws Exception { // AC-002-09
      Map<String, Object> body = TestData.student();
      body.put("optionIds", List.of("ws-legacy"));

      MvcResult result = postStudent(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertFieldError(json(result), "optionIds", "INACTIVE_OPTION");
      assertNothingStored();
    }

    @Test
    void rejectsMaliciousOptionIdentifier() throws Exception { // SECURITY option ids
      Map<String, Object> body = TestData.external();
      body.put("optionIds", List.of("../../etc/passwd", "' OR 1=1 --"));

      MvcResult result = postExternal(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertFieldError(json(result), "optionIds", "UNKNOWN_OPTION");
      assertNothingStored();
    }

    @Test
    void rejectsTooManyOptionIds() throws Exception { // spec §10.2
      Map<String, Object> body = TestData.external();
      List<String> ids = new ArrayList<>();
      for (int i = 0; i < 51; i++) {
        ids.add("ws-ai-practice");
      }
      body.put("optionIds", ids);

      MvcResult result = postExternal(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertFieldError(json(result), "optionIds", "TOO_MANY_OPTIONS");
    }

    @Test
    void collapsesDuplicateOptionIds() throws Exception { // spec §4.3
      Map<String, Object> body = TestData.external();
      body.put("optionIds", List.of("ws-ai-practice", "ws-ai-practice"));

      MvcResult result = postExternal(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(201);
      Registration stored =
          repository
              .findById(UUID.fromString(json(result).get("registrationId").asText()))
              .orElseThrow();
      assertThat(stored.getOptions()).hasSize(1);
    }
  }

  @Nested
  class AntiAutomation {

    @Test
    void rejectsMissingCaptchaToken() throws Exception { // spec §7.3
      Map<String, Object> body = TestData.external();
      body.remove("captchaToken");

      MvcResult result = postExternal(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertThat(json(result).get("error").asText()).isEqualTo("CAPTCHA_FAILED");
      assertNothingStored();
    }

    @Test
    void rejectsInvalidCaptchaToken() throws Exception { // spec §7.3
      Map<String, Object> body = TestData.student();
      body.put("captchaToken", "forged-token");

      MvcResult result = postStudent(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertThat(json(result).get("error").asText()).isEqualTo("CAPTCHA_FAILED");
      assertNothingStored();
    }
  }

  @Nested
  class BackupAndEmail {

    @Test
    void writesRawJsonBackupForAcceptedRegistration() throws Exception { // spec §6.2, US-005
      MvcResult result = postStudent(TestData.student());
      String id = json(result).get("registrationId").asText();

      List<Path> files;
      try (Stream<Path> stream = Files.list(BACKUP_DIR)) {
        files = stream.toList();
      }
      assertThat(files).hasSize(1);
      assertThat(files.get(0).getFileName().toString()).endsWith("_" + id + ".json");
      JsonNode backup = objectMapper.readTree(files.get(0).toFile());
      assertThat(backup.get("registrationId").asText()).isEqualTo(id);
      assertThat(backup.get("registrationType").asText()).isEqualTo("STUDENT");
      assertThat(backup.get("firstName").asText()).isEqualTo("Žiga");
      assertThat(backup.get("studentId").asText()).isEqualTo("63210001");
      assertThat(backup.get("consents").get("privacy").asBoolean()).isTrue();
      assertThat(backup.get("options").get(0).get("id").asText()).isEqualTo("ev-conference-dinner");
      assertThat(backup.has("captchaToken")).isFalse();
    }

    @Test
    void sendsParticipantConfirmationEmail() throws Exception { // AC-006-01
      postExternal(TestData.external());

      List<MimeMessage> messages = sentMessages();
      MimeMessage confirmation =
          messages.stream()
              .filter(m -> uncheckedRecipients(m).contains("ana.novak@example.si"))
              .findFirst()
              .orElseThrow();
      assertThat(confirmation.getSubject()).isEqualTo("Conference registration confirmed");
      String text = textOf(confirmation);
      assertThat(text).contains("Dear Ana").contains("received successfully");
      assertThat(text).contains("Workshop: Artificial intelligence in practice");
    }

    @Test
    void sendsOrganizerNotificationWithDataAndJsonAttachment() throws Exception { // AC-007-01
      MvcResult result = postStudent(TestData.student());
      String id = json(result).get("registrationId").asText();

      MimeMessage notification =
          sentMessages().stream()
              .filter(m -> uncheckedRecipients(m).contains(TestData.ORGANIZER_EMAIL))
              .findFirst()
              .orElseThrow();
      assertThat(notification.getSubject()).isEqualTo("New conference registration (STUDENT)");
      String text = textOf(notification);
      assertThat(text)
          .contains("Žiga")
          .contains("Čeh")
          .contains("ziga.ceh@student.uni-lj.si")
          .contains("Univerza v Ljubljani")
          .contains("Računalništvo in informatika")
          .contains("63210001")
          .contains("Conference dinner");
      Part attachment = attachment(notification);
      assertThat(attachment.getFileName()).isEqualTo("registration-" + id + ".json");
      JsonNode attached = objectMapper.readTree(attachment.getInputStream().readAllBytes());
      assertThat(attached.get("registrationId").asText()).isEqualTo(id);
    }

    @Test
    void registrationSucceedsEvenIfEmailDeliveryFails() throws Exception { // spec §8.3
      Mockito.doThrow(new org.springframework.mail.MailSendException("smtp down"))
          .when(mailSender)
          .send(any(MimeMessage.class));

      MvcResult result = postExternal(TestData.external());

      assertThat(result.getResponse().getStatus()).isEqualTo(201);
      assertThat(repository.count()).isEqualTo(1);
    }

    private List<String> uncheckedRecipients(MimeMessage message) {
      try {
        return recipients(message);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  @Nested
  class FormConfiguration {

    @Test
    void returnsOnlyActiveOptionsGroupedByCategory() throws Exception { // AC-003-01, AC-003-02
      MvcResult result = mockMvc.perform(get("/api/form-config")).andReturn();

      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      JsonNode body = json(result);
      List<String> ids = new ArrayList<>();
      List<String> categories = new ArrayList<>();
      body.get("options")
          .forEach(
              o -> {
                ids.add(o.get("id").asText());
                categories.add(o.get("category").asText());
                assertThat(o.get("name").asText()).isNotBlank();
              });
      assertThat(ids).doesNotContain("ws-legacy").contains("ws-ai-practice", "other-city-tour");
      assertThat(categories)
          .isSortedAccordingTo(
              (a, b) ->
                  List.of("WORKSHOP", "EVENT", "MEAL", "OTHER").indexOf(a)
                      - List.of("WORKSHOP", "EVENT", "MEAL", "OTHER").indexOf(b));
      assertThat(body.get("consents").get(0).get("id").asText()).isEqualTo("privacy");
      assertThat(body.get("consents").get(0).get("mandatory").asBoolean()).isTrue();
      assertThat(body.get("captcha").get("mode").asText()).isEqualTo("TEST");
      assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
    }
  }

  @Nested
  class Export {

    private byte[] export() throws Exception {
      MvcResult result =
          mockMvc
              .perform(
                  get("/api/organizer/registrations.xlsx")
                      .with(
                          org.springframework.security.test.web.servlet.request
                              .SecurityMockMvcRequestPostProcessors.httpBasic(
                              TestData.ORGANIZER_USERNAME, TestData.ORGANIZER_PASSWORD)))
              .andExpect(status().isOk())
              .andExpect(
                  header()
                      .string(
                          "Content-Type",
                          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
              .andExpect(
                  header()
                      .string(
                          "Content-Disposition",
                          org.hamcrest.Matchers.startsWith(
                              "attachment; filename=\"registrations-")))
              .andReturn();
      return result.getResponse().getContentAsByteArray();
    }

    private static List<List<String>> rows(byte[] xlsx) throws Exception {
      try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
        Sheet sheet = workbook.getSheet("Registrations");
        List<List<String>> rows = new ArrayList<>();
        for (Row row : sheet) {
          List<String> cells = new ArrayList<>();
          for (int i = 0; i < 15; i++) {
            cells.add(row.getCell(i) == null ? "" : row.getCell(i).getStringCellValue());
          }
          rows.add(cells);
        }
        return rows;
      }
    }

    @Test
    void organizerExportsAllRegistrationsAsExcel() throws Exception { // AC-008-01, AC-008-02
      String externalId = json(postExternal(TestData.external())).get("registrationId").asText();
      String studentId = json(postStudent(TestData.student())).get("registrationId").asText();

      List<List<String>> rows = rows(export());

      assertThat(rows).hasSize(3);
      assertThat(rows.get(0))
          .containsExactly(
              "Registration ID",
              "Submitted at (UTC)",
              "Type",
              "First name",
              "Last name",
              "Email",
              "Organization / institution",
              "Study institution",
              "Study programme",
              "Student ID",
              "Privacy consent",
              "Workshops",
              "Events",
              "Meals",
              "Other activities");
      List<String> external = rows.get(1);
      assertThat(external.get(0)).isEqualTo(externalId);
      assertThat(external.get(2)).isEqualTo("External participant");
      assertThat(external.subList(3, 7))
          .containsExactly("Ana", "Novak", "ana.novak@example.si", "Institut Jožef Stefan");
      assertThat(external.get(10)).isEqualTo("Yes");
      assertThat(external.get(11))
          .isEqualTo("Workshop: Artificial intelligence in practice [ws-ai-practice]");
      assertThat(external.get(13)).isEqualTo("Lunch – day 1 [meal-lunch-day1]");
      List<String> student = rows.get(2);
      assertThat(student.get(0)).isEqualTo(studentId);
      assertThat(student.get(2)).isEqualTo("Student");
      assertThat(student.subList(3, 10))
          .containsExactly(
              "Žiga",
              "Čeh",
              "ziga.ceh@student.uni-lj.si",
              "",
              "Univerza v Ljubljani",
              "Računalništvo in informatika",
              "63210001"); // AC-008-03 Unicode
      assertThat(student.get(12)).isEqualTo("Conference dinner [ev-conference-dinner]");
    }

    @Test
    void exportReflectsCurrentList() throws Exception { // AC-008-05, AC-005-04
      postExternal(TestData.external());
      assertThat(rows(export())).hasSize(2);

      postStudent(TestData.student());
      assertThat(rows(export())).hasSize(3);
    }

    @Test
    void emptyExportContainsOnlyHeader() throws Exception { // AC-008-06
      List<List<String>> rows = rows(export());

      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).get(0)).isEqualTo("Registration ID");
    }

    @Test
    void exportWithoutCredentialsIsDenied() throws Exception { // AC-008-04
      postExternal(TestData.external());

      MvcResult result = mockMvc.perform(get("/api/organizer/registrations.xlsx")).andReturn();

      assertThat(result.getResponse().getStatus()).isEqualTo(401);
      assertThat(result.getResponse().getHeader("WWW-Authenticate")).startsWith("Basic");
      assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
          .doesNotContain("Novak");
    }

    @Test
    void exportWithWrongPasswordIsDenied() throws Exception { // AC-008-04
      MvcResult result =
          mockMvc
              .perform(
                  get("/api/organizer/registrations.xlsx")
                      .with(
                          org.springframework.security.test.web.servlet.request
                              .SecurityMockMvcRequestPostProcessors.httpBasic(
                              TestData.ORGANIZER_USERNAME, "wrong")))
              .andReturn();

      assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }
  }

  @Nested
  class ErrorHandlingAndSecurity {

    @Test
    void malformedJsonIsRejectedWithoutDetails() throws Exception { // spec §4.5
      mockMvc
          .perform(
              post("/api/registrations/external")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"firstName\": "))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"))
          .andExpect(jsonPath("$.trace").doesNotExist())
          .andExpect(jsonPath("$.exception").doesNotExist());
      assertNothingStored();
    }

    @Test
    void unsupportedContentTypeIsRejected() throws Exception { // spec §4.5
      mockMvc
          .perform(
              post("/api/registrations/external")
                  .contentType(MediaType.TEXT_PLAIN)
                  .content("firstName=Ana"))
          .andExpect(status().isUnsupportedMediaType())
          .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void wrongMethodIsRejected() throws Exception { // spec §4.5
      mockMvc
          .perform(get("/api/registrations/external"))
          .andExpect(status().isMethodNotAllowed())
          .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unknownApiPathReturnsNotFound() throws Exception { // spec §4.5
      mockMvc
          .perform(get("/api/does-not-exist"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void oversizedBodyIsRejected() throws Exception { // spec §7.7
      Map<String, Object> body = TestData.external();
      body.put("organization", "x".repeat(20_000));

      MvcResult result = postExternal(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(413);
      assertThat(json(result).get("error").asText()).isEqualTo("PAYLOAD_TOO_LARGE");
      assertNothingStored();
    }

    @Test
    void securityHeadersArePresent() throws Exception { // spec §7.6
      mockMvc
          .perform(get("/api/form-config"))
          .andExpect(header().string("X-Content-Type-Options", "nosniff"))
          .andExpect(header().string("X-Frame-Options", "DENY"))
          .andExpect(header().string("Referrer-Policy", "no-referrer"))
          .andExpect(
              header()
                  .string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
    }

    @Test
    void nonApiPathsAreDenied() throws Exception { // spec §7.4
      assertThat(mockMvc.perform(get("/actuator/env")).andReturn().getResponse().getStatus())
          .isIn(401, 403);
    }

    @Test
    void scriptInjectionIsStoredAsPlainDataAndReturnedAsJsonOnly() throws Exception { // §7.2
      Map<String, Object> body = TestData.external();
      body.put("organization", "<script>alert(1)</script>");

      MvcResult result = postExternal(body);

      assertThat(result.getResponse().getStatus()).isEqualTo(201);
      assertThat(result.getResponse().getContentType()).startsWith("application/json");
      assertThat(result.getResponse().getContentAsString()).doesNotContain("<script>");
    }

    @Test
    void healthEndpointsAreAvailable() throws Exception { // spec §13
      mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
      mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
      mockMvc
          .perform(get("/actuator/health"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("UP"))
          .andExpect(jsonPath("$.components").doesNotExist());
    }
  }
}
