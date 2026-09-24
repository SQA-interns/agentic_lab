package org.conference.registration.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** SMTP unavailable: registration still succeeds and the app stays healthy (AC-006-03). */
@TestPropertySource(properties = {"spring.mail.port=1", "spring.mail.host=127.0.0.1"})
class MailOutageIT extends AbstractIntegrationTest {

  @Test
  void registrationSucceedsWhenMailServerIsDown() throws Exception {
    postJson("/api/registrations/external", externalPayload()).andExpect(status().isCreated());
    assertThat(registrationCount()).isEqualTo(1);
    assertThat(backupFileCount()).isEqualTo(1);
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }
}
