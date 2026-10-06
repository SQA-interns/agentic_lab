package si.konferenca.registration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainModelTest {

  private static ConferenceOption option(String id, boolean active) {
    return new ConferenceOption(
        id, id, OptionCategory.EVENT, active, EnumSet.allOf(RegistrationType.class));
  }

  @Test
  void catalogRejectsRepeatedOptionOrConsentIds() {
    assertThatThrownBy(
            () -> new ConferenceCatalog(List.of(option("a", true), option("a", false)), List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate option id a");
    assertThatThrownBy(
            () ->
                new ConferenceCatalog(
                    List.of(),
                    List.of(
                        new ConsentDefinition("c", "x", true),
                        new ConsentDefinition("c", "y", false))))
        .hasMessageContaining("duplicate consent id c");
  }

  @Test
  void catalogFindsOptionsAndConsentsAndListsActiveOptionsInOrder() {
    ConferenceCatalog catalog =
        new ConferenceCatalog(
            List.of(option("b", true), option("off", false), option("a", true)),
            List.of(new ConsentDefinition("c", "x", true)));

    assertThat(catalog.option("off")).isPresent();
    assertThat(catalog.option("missing")).isEmpty();
    assertThat(catalog.consent("c")).isPresent();
    assertThat(catalog.consent("d")).isEmpty();
    assertThat(catalog.activeOptions()).extracting(ConferenceOption::id).containsExactly("b", "a");
  }

  @Test
  void catalogListsAreUnmodifiableCopies() {
    List<ConferenceOption> options = new ArrayList<>(List.of(option("a", true)));
    ConferenceCatalog catalog = new ConferenceCatalog(options, List.of());
    options.add(option("b", true));

    assertThat(catalog.options()).hasSize(1);
  }

  @Test
  void optionMustBeOfferedToSomeoneAndKnowsItsAudience() {
    assertThatThrownBy(() -> new ConferenceOption("x", "X", OptionCategory.MEAL, true, Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
    ConferenceOption studentsOnly =
        new ConferenceOption("x", "X", OptionCategory.MEAL, true, Set.of(RegistrationType.STUDENT));
    assertThat(studentsOnly.isOfferedTo(RegistrationType.STUDENT)).isTrue();
    assertThat(studentsOnly.isOfferedTo(RegistrationType.EXTERNAL)).isFalse();
  }

  @Test
  void typesAndCategoriesMapTheirValues() {
    assertThat(RegistrationType.fromValue("student")).contains(RegistrationType.STUDENT);
    assertThat(RegistrationType.fromValue(null)).isEmpty();
    assertThat(RegistrationType.EXTERNAL.label()).isEqualTo("External participant");
    assertThat(OptionCategory.fromValue("other")).contains(OptionCategory.OTHER);
    assertThat(OptionCategory.fromValue("Other")).isEmpty();
    assertThat(OptionCategory.MEAL.label()).isEqualTo("Meals");
  }

  @Test
  void emailFormatAndNormalisation() {
    assertThat(EmailAddress.isValid("a@b.si")).isTrue();
    assertThat(EmailAddress.isValid("a@b")).isFalse();
    assertThat(EmailAddress.isValid(null)).isFalse();
    assertThat(EmailAddress.isValid("a".repeat(249) + "@b.si")).isTrue();
    assertThat(EmailAddress.isValid("a".repeat(250) + "@b.si")).isFalse();
    assertThat(EmailAddress.normalize("  Ana.Novak@Example.SI ")).isEqualTo("ana.novak@example.si");
  }

  @Test
  void registrationGroupsOptionNamesByCategory() {
    Registration r =
        new Registration(
            UUID.randomUUID(),
            RegistrationType.EXTERNAL,
            new Participant("A", "N", "a@b.si", "O", null, null, null),
            List.of(
                new Registration.SelectedOption("m", "Lunch", OptionCategory.MEAL),
                new Registration.SelectedOption("w", "WS", OptionCategory.WORKSHOP),
                new Registration.SelectedOption("d", "Dinner", OptionCategory.MEAL)),
            List.of(),
            Instant.EPOCH);

    assertThat(r.optionNames(OptionCategory.MEAL)).containsExactly("Lunch", "Dinner");
    assertThat(r.optionNames(OptionCategory.OTHER)).isEmpty();
    assertThat(r.participant().fullName()).isEqualTo("A N");
  }

  @Test
  void fieldErrorUsesTheDefaultMessageOfItsCode() {
    assertThat(FieldError.of("email", ErrorCode.DUPLICATE_EMAIL).message())
        .contains("contact the organizers");
  }
}
