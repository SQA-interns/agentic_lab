package si.konferenca.registration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import si.konferenca.registration.domain.RegistrationRepository;
import si.konferenca.registration.service.RegistrationBackupStore;
import si.konferenca.registration.support.PostgresIntegrationTest;
import si.konferenca.registration.support.TestData;

/** AC-004-03: a registration that cannot be stored is not confirmed and not persisted. */
@SpringBootTest
@AutoConfigureMockMvc
class BackupFailureIntegrationTest extends PostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RegistrationRepository repository;

  @MockitoBean private RegistrationBackupStore backupStore;
  @MockitoBean private JavaMailSender mailSender;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
    doThrow(new UncheckedIOException(new java.io.IOException("disk full")))
        .when(backupStore)
        .store(any(UUID.class), any(Instant.class), any(byte[].class));
  }

  @Test
  void backupFailureRollsBackAndReturnsErrorWithoutConfirmation() throws Exception {
    mockMvc
        .perform(
            post("/api/registrations/external")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(TestData.external())))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.registrationId").doesNotExist())
        .andExpect(
            jsonPath("$.message")
                .value(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("disk full"))));

    assertThat(repository.count()).isZero();
    verify(mailSender, never()).send(any(MimeMessage.class));
  }
}
