package org.conference.registration.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared integration-test infrastructure: one real PostgreSQL 16 container (Testcontainers) and one
 * GreenMail SMTP server per JVM, a temporary backup directory, and MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.mail.host=127.0.0.1",
      "spring.mail.port=3025", // GreenMail ServerSetupTest.SMTP
      "app.conference-name=Test Conference",
      "app.mail.from=registration@conference.test",
      "app.mail.organizers=organizers@conference.test,chair@conference.test",
      "app.admin.username=" + AbstractIntegrationTest.ADMIN_USER,
      "app.admin.password=" + AbstractIntegrationTest.ADMIN_PASSWORD,
      "app.antibot.form-token-secret=integration-test-secret-0123456789abcdef",
      "app.antibot.min-fill-seconds=0",
      "app.rate-limit.registrations=100000",
      "app.rate-limit.form-tokens=100000",
      "app.rate-limit.admin=100000"
    })
public abstract class AbstractIntegrationTest {

  protected static final String ADMIN_USER = "organizer";
  protected static final String ADMIN_PASSWORD = "test-organizer-password";

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
  protected static final GreenMail GREEN_MAIL = new GreenMail(ServerSetupTest.SMTP);
  protected static final Path BACKUP_DIR;

  static {
    POSTGRES.start();
    GREEN_MAIL.start();
    try {
      BACKUP_DIR = Files.createTempDirectory("registration-backups");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Only infrastructure endpoints are dynamic; other settings live in @TestPropertySource. */
  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("app.backup.dir", BACKUP_DIR::toString);
  }

  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected ObjectMapper objectMapper;

  @BeforeEach
  void resetState() throws Exception {
    jdbc.execute("TRUNCATE registration CASCADE");
    GREEN_MAIL.purgeEmailFromAllMailboxes();
    try (Stream<Path> files = Files.list(BACKUP_DIR)) {
      for (Path file : files.toList()) {
        Files.delete(file);
      }
    }
  }

  protected String formToken() throws Exception {
    String body =
        mvc.perform(get("/api/form-token")).andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).get("token").asText();
  }

  protected Map<String, Object> externalPayload() throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("firstName", "Ana");
    payload.put("lastName", "Novak");
    payload.put("email", "ana.novak@example.si");
    payload.put("organization", "Univerza v Mariboru");
    payload.put("privacyConsent", true);
    payload.put("optionIds", List.of("ws-secure-coding", "meal-lunch-day1"));
    payload.put("formToken", formToken());
    payload.put("website", "");
    return payload;
  }

  protected Map<String, Object> studentPayload() throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("firstName", "Špela");
    payload.put("lastName", "Kovač");
    payload.put("email", "spela.kovac@student.um.si");
    payload.put("studyInstitution", "FERI Maribor");
    payload.put("studyProgramme", "Računalništvo in informacijske tehnologije");
    payload.put("studentId", "E1234567");
    payload.put("privacyConsent", true);
    payload.put("optionIds", List.of("ev-career-fair"));
    payload.put("formToken", formToken());
    payload.put("website", "");
    return payload;
  }

  protected ResultActions postJson(String path, Object body) throws Exception {
    return postJson(path, body, "127.0.0.1");
  }

  protected ResultActions postJson(String path, Object body, String remoteAddr) throws Exception {
    return mvc.perform(
        post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(body))
            .with(
                request -> {
                  request.setRemoteAddr(remoteAddr);
                  return request;
                }));
  }

  protected JsonNode json(ResultActions result) throws Exception {
    return objectMapper.readTree(result.andReturn().getResponse().getContentAsByteArray());
  }

  protected int registrationCount() {
    Integer count = jdbc.queryForObject("SELECT count(*) FROM registration", Integer.class);
    return count == null ? 0 : count;
  }

  /**
   * Emails are sent asynchronously after commit, so mails of an earlier test may still arrive
   * during a later one. Assertions therefore select messages by a marker (e.g. registration id) and
   * poll until the expected number arrived or the timeout elapsed.
   */
  protected List<MimeMessage> awaitMessagesContaining(String marker, int expected, long timeoutMs)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    List<MimeMessage> found;
    do {
      found =
          Stream.of(GREEN_MAIL.getReceivedMessages())
              .filter(m -> rawMessage(m).contains(marker))
              .toList();
      if (found.size() >= expected) {
        return found;
      }
      Thread.sleep(100);
    } while (System.currentTimeMillis() < deadline);
    return found;
  }

  protected static String rawMessage(MimeMessage message) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      message.writeTo(out);
      return out.toString(StandardCharsets.UTF_8);
    } catch (IOException | MessagingException e) {
      throw new IllegalStateException(e);
    }
  }

  protected static List<String> recipients(MimeMessage message) {
    try {
      return Stream.of(message.getAllRecipients()).map(Object::toString).toList();
    } catch (MessagingException e) {
      throw new IllegalStateException(e);
    }
  }

  protected long backupFileCount() throws IOException {
    try (Stream<Path> files = Files.list(BACKUP_DIR)) {
      return files.count();
    }
  }
}
