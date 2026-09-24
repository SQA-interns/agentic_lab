package org.conference.registration.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Minimum fill time and rate limiting with production-like limits (AC-PC-04, AC-PC-05). */
@TestPropertySource(
    properties = {"app.antibot.min-fill-seconds=2", "app.rate-limit.registrations=3"})
class AntiAutomationIT extends AbstractIntegrationTest {

  @Test
  void submissionFasterThanMinimumFillTimeIsRejected() throws Exception { // AC-PC-04
    Map<String, Object> payload = externalPayload();
    postJson("/api/registrations/external", payload, "10.0.0.1")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("SUBMISSION_REJECTED"));
    assertThat(registrationCount()).isZero();

    Thread.sleep(2_100);
    postJson("/api/registrations/external", payload, "10.0.0.1").andExpect(status().isCreated());
  }

  @Test
  void clientExceedingRateLimitGets429WithSecurityHeaders() throws Exception { // AC-PC-05
    Map<String, Object> invalid = Map.of("firstName", "x");
    for (int i = 0; i < 3; i++) {
      postJson("/api/registrations/external", invalid, "10.0.0.2")
          .andExpect(status().isBadRequest());
    }
    postJson("/api/registrations/external", invalid, "10.0.0.2")
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(jsonPath("$.error").value("RATE_LIMITED"));

    // Another client is unaffected.
    postJson("/api/registrations/external", invalid, "10.0.0.3").andExpect(status().isBadRequest());
  }
}
