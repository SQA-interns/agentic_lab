package org.conference.registration.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import org.conference.registration.service.BackupFailedException;
import org.conference.registration.service.BackupWriter;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Storage failure must not be reported as success (AC-005-04). */
class FailureHandlingIT extends AbstractIntegrationTest {

  @MockitoSpyBean BackupWriter backupWriter;

  @Test
  void backupFailureRollsBackRegistrationAndSendsNoMail() throws Exception {
    doThrow(new BackupFailedException("disk full", new IOException("disk full")))
        .when(backupWriter)
        .write(anyString(), any());

    var payload = externalPayload();
    payload.put("lastName", "Rollbackmarker");
    postJson("/api/registrations/external", payload)
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"));

    assertThat(registrationCount()).isZero();
    assertThat(awaitMessagesContaining("Rollbackmarker", 1, 1_500)).isEmpty();
  }
}
