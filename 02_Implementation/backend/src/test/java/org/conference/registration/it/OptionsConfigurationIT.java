package org.conference.registration.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** A different options file changes the offered options without code changes (AC-003-02). */
@TestPropertySource(properties = "app.options.file=classpath:options-alternative.json")
class OptionsConfigurationIT extends AbstractIntegrationTest {

  @Test
  void alternativeConfigurationIsServedAndEnforced() throws Exception {
    JsonNode options =
        json(mvc.perform(get("/api/options").param("type", "EXTERNAL")).andExpect(status().isOk()));
    assertThat(options).hasSize(1);
    assertThat(options.get(0).get("id").asText()).isEqualTo("ws-new-topic");

    Map<String, Object> payload = externalPayload();
    payload.put("optionIds", List.of("ws-secure-coding"));
    postJson("/api/registrations/external", payload).andExpect(status().isBadRequest());

    payload.put("optionIds", List.of("ws-new-topic"));
    postJson("/api/registrations/external", payload).andExpect(status().isCreated());
  }
}
