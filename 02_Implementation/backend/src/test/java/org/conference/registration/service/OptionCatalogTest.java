package org.conference.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.conference.registration.config.AppProperties;
import org.conference.registration.domain.ConferenceOption;
import org.conference.registration.domain.OptionCategory;
import org.conference.registration.domain.RegistrationType;
import org.conference.registration.service.OptionCatalog.OptionDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class OptionCatalogTest {

  private static final Set<RegistrationType> BOTH =
      Set.of(RegistrationType.EXTERNAL, RegistrationType.STUDENT);

  private final OptionCatalog catalog =
      new OptionCatalog(
          List.of(
              new OptionDefinition("ws-a", OptionCategory.WORKSHOP, "Workshop A", true, BOTH),
              new OptionDefinition("ws-off", OptionCategory.WORKSHOP, "Old", false, BOTH),
              new OptionDefinition(
                  "ev-ext", OptionCategory.EVENT, "Gala", true, Set.of(RegistrationType.EXTERNAL)),
              new OptionDefinition(
                  "ev-stu", OptionCategory.EVENT, "Fair", true, Set.of(RegistrationType.STUDENT)),
              new OptionDefinition("meal-1", OptionCategory.MEAL, "Lunch", true, null)));

  @Test
  void activeForReturnsOnlyActiveOptionsOfTheAudienceInConfigOrder() {
    assertThat(catalog.activeFor(RegistrationType.EXTERNAL))
        .extracting(ConferenceOption::id)
        .containsExactly("ws-a", "ev-ext", "meal-1");
    assertThat(catalog.activeFor(RegistrationType.STUDENT))
        .extracting(ConferenceOption::id)
        .containsExactly("ws-a", "ev-stu", "meal-1");
  }

  @Test
  void resolveReturnsSelectedOptions() {
    assertThat(catalog.resolve(RegistrationType.STUDENT, List.of("ev-stu", "ws-a")))
        .extracting(ConferenceOption::id)
        .containsExactly("ev-stu", "ws-a");
  }

  @Test
  void resolveAcceptsNoSelection() {
    assertThat(catalog.resolve(RegistrationType.EXTERNAL, List.of())).isEmpty();
    assertThat(catalog.resolve(RegistrationType.EXTERNAL, null)).isEmpty();
  }

  @Test
  void resolveRejectsUnknownOption() {
    assertThatThrownBy(() -> catalog.resolve(RegistrationType.EXTERNAL, List.of("nope")))
        .isInstanceOf(RegistrationValidationException.class)
        .extracting("field")
        .isEqualTo("optionIds");
  }

  @Test
  void resolveRejectsInactiveOption() {
    assertThatThrownBy(() -> catalog.resolve(RegistrationType.EXTERNAL, List.of("ws-off")))
        .isInstanceOf(RegistrationValidationException.class);
  }

  @Test
  void resolveRejectsOptionNotAvailableToAudience() {
    assertThatThrownBy(() -> catalog.resolve(RegistrationType.STUDENT, List.of("ev-ext")))
        .isInstanceOf(RegistrationValidationException.class);
  }

  @Test
  void resolveRejectsDuplicates() {
    assertThatThrownBy(() -> catalog.resolve(RegistrationType.EXTERNAL, List.of("ws-a", "ws-a")))
        .isInstanceOf(RegistrationValidationException.class)
        .hasMessageContaining("repeated");
  }

  @Test
  void duplicateIdFailsStartup() {
    List<OptionDefinition> defs =
        List.of(
            new OptionDefinition("x", OptionCategory.MEAL, "A", true, BOTH),
            new OptionDefinition("x", OptionCategory.MEAL, "B", true, BOTH));
    assertThatThrownBy(() -> new OptionCatalog(defs))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate");
  }

  @Test
  void missingNameFailsStartup() {
    List<OptionDefinition> defs =
        List.of(new OptionDefinition("x", OptionCategory.MEAL, " ", true, BOTH));
    assertThatThrownBy(() -> new OptionCatalog(defs)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void invalidIdFailsStartup() {
    List<OptionDefinition> defs =
        List.of(new OptionDefinition("Bad Id", OptionCategory.MEAL, "A", true, BOTH));
    assertThatThrownBy(() -> new OptionCatalog(defs)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void missingActiveFlagFailsStartup() {
    List<OptionDefinition> defs =
        List.of(new OptionDefinition("x", OptionCategory.MEAL, "A", null, BOTH));
    assertThatThrownBy(() -> new OptionCatalog(defs))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("active");
  }

  @Test
  void missingCategoryFailsStartup() {
    List<OptionDefinition> defs = List.of(new OptionDefinition("x", null, "A", true, BOTH));
    assertThatThrownBy(() -> new OptionCatalog(defs)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void loadsBundledDefaultConfiguration() {
    AppProperties props = TestProperties.withOptionsFile("classpath:conference-options.json");
    OptionCatalog loaded =
        new OptionCatalog(props, new DefaultResourceLoader(), new ObjectMapper());
    assertThat(loaded.activeFor(RegistrationType.EXTERNAL)).isNotEmpty();
    assertThat(loaded.activeFor(RegistrationType.STUDENT))
        .extracting(ConferenceOption::id)
        .doesNotContain("ws-legacy");
  }

  @Test
  void missingFileFailsStartup() {
    AppProperties props = TestProperties.withOptionsFile("classpath:does-not-exist.json");
    assertThatThrownBy(
            () -> new OptionCatalog(props, new DefaultResourceLoader(), new ObjectMapper()))
        .isInstanceOf(IllegalStateException.class);
  }
}
