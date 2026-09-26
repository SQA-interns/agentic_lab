package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.TestFixtures;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionCategory;

class ConferenceCatalogTest {

  private static ConferenceCatalog catalog(AppProperties.Conference conference) {
    return new ConferenceCatalog(TestFixtures.properties(conference));
  }

  @Test
  void offersOnlyActiveOptionsInConfigurationOrder() {
    ConferenceCatalog catalog = catalog(TestFixtures.conference());
    assertThat(catalog.activeOptions())
        .extracting(ConferenceOption::id)
        .containsExactly("ws-a", "ev-dinner", "meal-lunch", "other-tour");
  }

  @Test
  void findsActiveButNotInactiveOrUnknownOptions() {
    ConferenceCatalog catalog = catalog(TestFixtures.conference());
    assertThat(catalog.findActiveOption("ws-a")).isPresent();
    assertThat(catalog.findActiveOption("ws-old")).isEmpty();
    assertThat(catalog.findActiveOption("missing")).isEmpty();
    assertThat(catalog.findActiveOption(null)).isEmpty();
  }

  @Test
  void everyOptionHasIdentifierNameAndStatus() {
    ConferenceOption option = catalog(TestFixtures.conference()).activeOptions().getFirst();
    assertThat(option.id()).isEqualTo("ws-a");
    assertThat(option.name()).isEqualTo("Workshop A");
    assertThat(option.active()).isTrue();
    assertThat(option.category()).isEqualTo(OptionCategory.WORKSHOP);
  }

  @Test
  void reflectsChangedConfiguration() {
    AppProperties.Conference changed =
        new AppProperties.Conference(
            List.of(
                new AppProperties.OptionEntry("ws-a", "Renamed", OptionCategory.WORKSHOP, true),
                new AppProperties.OptionEntry("ws-new", "New", OptionCategory.WORKSHOP, true),
                new AppProperties.OptionEntry("ev-dinner", "Dinner", OptionCategory.EVENT, false)),
            List.of());
    ConferenceCatalog catalog = catalog(changed);
    assertThat(catalog.activeOptions())
        .extracting(ConferenceOption::name)
        .containsExactly("Renamed", "New");
    assertThat(catalog.consents()).isEmpty();
  }

  @Test
  void rejectsDuplicateOptionIds() {
    AppProperties.Conference duplicate =
        new AppProperties.Conference(
            List.of(
                new AppProperties.OptionEntry("ws-a", "A", OptionCategory.WORKSHOP, true),
                new AppProperties.OptionEntry("ws-a", "B", OptionCategory.EVENT, true)),
            List.of());
    assertThatThrownBy(() -> catalog(duplicate)).hasMessageContaining("Duplicate");
  }

  @Test
  void rejectsInvalidIdentifier() {
    AppProperties.Conference invalid =
        new AppProperties.Conference(
            List.of(new AppProperties.OptionEntry("Bad Id!", "A", OptionCategory.WORKSHOP, true)),
            List.of());
    assertThatThrownBy(() -> catalog(invalid)).hasMessageContaining("Invalid");
  }

  @Test
  void rejectsMissingCategoryOrName() {
    AppProperties.Conference noCategory =
        new AppProperties.Conference(
            List.of(new AppProperties.OptionEntry("ws-a", "A", null, true)), List.of());
    AppProperties.Conference noName =
        new AppProperties.Conference(
            List.of(new AppProperties.OptionEntry("ws-a", " ", OptionCategory.MEAL, true)),
            List.of());
    assertThatThrownBy(() -> catalog(noCategory)).hasMessageContaining("category");
    assertThatThrownBy(() -> catalog(noName)).hasMessageContaining("name");
  }

  @Test
  void rejectsDuplicateConsentIds() {
    AppProperties.Conference duplicate =
        new AppProperties.Conference(
            List.of(),
            List.of(
                new AppProperties.ConsentEntry("privacy", "A", true),
                new AppProperties.ConsentEntry("privacy", "B", false)));
    assertThatThrownBy(() -> catalog(duplicate)).hasMessageContaining("Duplicate consent");
  }
}
