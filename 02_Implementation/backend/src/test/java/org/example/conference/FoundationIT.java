package org.example.conference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.example.conference.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

/** M0: Flyway migration on real PostgreSQL, readiness/liveness, form-config endpoint. */
class FoundationIT extends IntegrationTestBase {

  @Test
  void flywayCreatedSchemaOnPostgres() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema='public'",
            String.class);
    assertThat(tables)
        .contains(
            "registration",
            "registration_selection",
            "registration_consent",
            "email_outbox",
            "flyway_schema_history");
    Integer success =
        jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
    assertThat(success).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT version()", String.class)).contains("PostgreSQL 16");
  }

  @Test
  void readinessAndLivenessAreUp() throws Exception {
    mockMvc
        .perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mockMvc
        .perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void formConfigListsOnlyActiveOptions() throws Exception {
    mockMvc
        .perform(get("/api/form-config"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.optionGroups.workshops[*].id")
                .value(org.hamcrest.Matchers.contains("ws-data-science", "ws-open-source")))
        .andExpect(jsonPath("$.optionGroups.meals.length()").value(3))
        .andExpect(jsonPath("$.consents[0].required").value(true))
        .andExpect(jsonPath("$.captcha.mode").value("test"));
  }
}
