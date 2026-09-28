package org.example.conference.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.conference.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** AR-06/AR-08 HTTP controls: headers, deny-by-default, body size, error privacy. */
class SecurityIT extends IntegrationTestBase {

  @Test
  void securityHeadersArePresent() throws Exception {
    mockMvc
        .perform(get("/api/form-config"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(
            header()
                .string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
  }

  @Test
  void hstsIsSentForForwardedHttpsRequests() throws Exception {
    mockMvc
        .perform(get("/api/form-config").secure(true))
        .andExpect(header().exists("Strict-Transport-Security"));
  }

  @Test
  void unknownEndpointsAndActuatorInternalsAreDenied() throws Exception {
    for (String path :
        new String[] {"/actuator/env", "/actuator/beans", "/api/registrations", "/admin"}) {
      int statusCode = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
      assertThat(statusCode).as(path).isIn(401, 403);
    }
  }

  @Test
  void healthDoesNotExposeDetails() throws Exception {
    String body =
        mockMvc
            .perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // Probe group names are not sensitive; component details (db, disk, paths) must not appear.
    assertThat(body)
        .contains("\"status\":\"UP\"")
        .doesNotContain("components")
        .doesNotContain("details")
        .doesNotContain("postgres");
  }

  @Test
  void oversizedBodyIsRejected() throws Exception {
    String huge = "{\"firstName\":\"" + "a".repeat(40_000) + "\"}";
    mockMvc
        .perform(
            post("/api/registrations/external")
                .contentType(MediaType.APPLICATION_JSON)
                .content(huge))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    assertThat(registrationCount()).isZero();
  }

  @Test
  void wrongMediaTypeAndMalformedJsonAreRejectedWithoutDetails() throws Exception {
    mockMvc
        .perform(
            post("/api/registrations/external")
                .contentType(MediaType.TEXT_PLAIN)
                .content("firstName=x"))
        .andExpect(status().isUnsupportedMediaType());
    String body =
        mockMvc
            .perform(
                post("/api/registrations/external")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"firstName\": \"Secret Name\""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain("Secret Name").doesNotContain("Exception");
  }
}
