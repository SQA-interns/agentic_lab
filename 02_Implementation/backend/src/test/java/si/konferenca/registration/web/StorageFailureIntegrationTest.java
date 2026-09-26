package si.konferenca.registration.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import si.konferenca.registration.IntegrationTestBase;
import si.konferenca.registration.RegistrationJson;
import si.konferenca.registration.application.port.RegistrationBackup;

class StorageFailureIntegrationTest extends IntegrationTestBase {

  @MockitoBean RegistrationBackup backup;

  @Test
  void backupFailureRollsBackDatabaseAndReturnsNoConfirmation() throws Exception {
    doThrow(new IOException("disk full")).when(backup).write(any(), anyString());

    String body =
        mvc.perform(
                post("/api/registrations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .characterEncoding(StandardCharsets.UTF_8)
                    .content(RegistrationJson.external().build()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("REGISTRATION_FAILED"))
            .andExpect(jsonPath("$.registrationId").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).doesNotContain("disk full");
    assertThat(registrationCount()).isZero();
    verify(mailSender, never()).send(any(MimeMessage.class));
  }
}
