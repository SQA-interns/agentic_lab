package si.konferenca.registration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OptionCatalogTest {

  private static ConferenceOption option(String id, boolean active) {
    return new ConferenceOption(
        id,
        id.toUpperCase(),
        OptionCategory.WORKSHOP,
        active,
        EnumSet.allOf(RegistrationType.class));
  }

  private static final Consent CONSENT = new Consent("c", "text");

  @Test
  void duplicateIdentifierIsRejectedWithItsName() {
    List<ConferenceOption> options = List.of(option("a", true), option("a", false));

    assertThatThrownBy(() -> new OptionCatalog(CONSENT, options))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'a'");
  }

  @Test
  void activeOptionsKeepConfigurationOrderAndSkipInactive() {
    OptionCatalog catalog =
        new OptionCatalog(
            CONSENT, List.of(option("b", true), option("x", false), option("a", true)));

    assertThat(catalog.activeOptions()).extracting(ConferenceOption::id).containsExactly("b", "a");
    assertThat(catalog.find("x")).isPresent();
    assertThat(catalog.find("missing")).isEmpty();
    assertThat(catalog.consent()).isEqualTo(CONSENT);
  }

  @Test
  void optionKnowsWhichTypesItIsOfferedTo() {
    ConferenceOption gala =
        new ConferenceOption(
            "g", "Gala", OptionCategory.EVENT, true, Set.of(RegistrationType.EXTERNAL));

    assertThat(gala.isOfferedTo(RegistrationType.EXTERNAL)).isTrue();
    assertThat(gala.isOfferedTo(RegistrationType.STUDENT)).isFalse();
  }

  @Test
  void labelsAreHumanReadable() {
    assertThat(RegistrationType.STUDENT.label()).isEqualTo("Student");
    assertThat(OptionCategory.OTHER.label()).isEqualTo("Other activities");
  }
}
