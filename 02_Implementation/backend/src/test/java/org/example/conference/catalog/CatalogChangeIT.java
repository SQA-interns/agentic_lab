package org.example.conference.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.example.conference.support.IntegrationTestBase;
import org.example.conference.support.Payloads;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * P-05 / AC-003-03: a fresh application context (= restart) with the next conference's catalog file
 * displays and enforces the new catalog without code changes; fixed fields are unchanged.
 */
class CatalogChangeIT extends IntegrationTestBase {

  @DynamicPropertySource
  static void changedCatalog(DynamicPropertyRegistry registry) {
    registry.add(
        "app.catalog.path",
        () -> Path.of("src/test/resources/catalog-changed.yaml").toAbsolutePath().toString());
  }

  @Test
  void changedCatalogIsDisplayed() throws Exception {
    mockMvc
        .perform(get("/api/form-config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conferenceName").value("Example Conference 2027"))
        .andExpect(
            jsonPath("$.optionGroups.workshops[*].id")
                .value(Matchers.contains("ws-data-science", "ws-ai-safety")))
        .andExpect(
            jsonPath("$.optionGroups.events[*].id").value(Matchers.contains("ev-harbour-cruise")));
  }

  @Test
  void retiredAndRemovedOptionsAreRejectedAndNewOnesAccepted() throws Exception {
    for (String retired : List.of("ws-open-source", "ws-legacy-cobol")) {
      Map<String, Object> body = Payloads.external();
      body.put("selections", Payloads.selections(List.of(retired), List.of()));
      postJson("/api/registrations/external", body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("OPTION_INVALID"));
    }
    Map<String, Object> removedMeal = Payloads.student();
    removedMeal.put("selections", Payloads.selections(List.of(), List.of("meal-gala-dinner")));
    postJson("/api/registrations/student", removedMeal).andExpect(status().isBadRequest());

    Map<String, Object> external = Payloads.external();
    external.put("selections", Payloads.selections(List.of("ws-ai-safety"), List.of()));
    postJson("/api/registrations/external", external).andExpect(status().isCreated());
    Map<String, Object> student = Payloads.student();
    student.put(
        "selections", Payloads.selections(List.of("ws-ai-safety"), List.of("meal-vegan-dinner")));
    postJson("/api/registrations/student", student).andExpect(status().isCreated());
  }
}
