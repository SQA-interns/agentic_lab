package si.konferenca.registration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OptionCatalogueTest {

  private static final Consent CONSENT = new Consent("data", "I agree", true);

  private static ConferenceOption option(String id, boolean active) {
    return new ConferenceOption(
        id, id, Category.EVENT, active, EnumSet.allOf(RegistrationType.class));
  }

  @Test
  void missingCategoryLimitDefaultsToOne() {
    OptionCatalogue c = new OptionCatalogue(Map.of(Category.MEAL, 3), List.of(), List.of(CONSENT));
    assertThat(c.limit(Category.MEAL)).isEqualTo(3);
    assertThat(c.limit(Category.WORKSHOP)).isEqualTo(OptionCatalogue.DEFAULT_LIMIT).isEqualTo(1);
  }

  @Test
  void activeOptionsKeepConfiguredOrder() {
    OptionCatalogue c =
        new OptionCatalogue(
            Map.of(),
            List.of(option("b", true), option("x", false), option("a", true)),
            List.of(CONSENT));
    assertThat(c.activeOptions()).extracting(ConferenceOption::id).containsExactly("b", "a");
    assertThat(c.option("x")).isPresent();
    assertThat(c.option("missing")).isEmpty();
  }

  @Test
  void duplicateOptionIdIsRejected() {
    assertThatThrownBy(
            () ->
                new OptionCatalogue(
                    Map.of(), List.of(option("a", true), option("a", false)), List.of(CONSENT)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate option id a");
  }

  @Test
  void duplicateConsentIdIsRejected() {
    assertThatThrownBy(() -> new OptionCatalogue(Map.of(), List.of(), List.of(CONSENT, CONSENT)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void atLeastOneMandatoryConsentIsRequired() {
    assertThatThrownBy(
            () ->
                new OptionCatalogue(
                    Map.of(), List.of(), List.of(new Consent("news", "Newsletter", false))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void consentLookup() {
    OptionCatalogue c = new OptionCatalogue(Map.of(), List.of(), List.of(CONSENT));
    assertThat(c.consent("data")).contains(CONSENT);
    assertThat(c.consent("other")).isEmpty();
  }

  @Test
  void optionAvailability() {
    ConferenceOption studentsOnly =
        new ConferenceOption("s", "S", Category.EVENT, true, EnumSet.of(RegistrationType.STUDENT));
    assertThat(studentsOnly.availableTo(RegistrationType.STUDENT)).isTrue();
    assertThat(studentsOnly.availableTo(RegistrationType.EXTERNAL)).isFalse();
  }
}
