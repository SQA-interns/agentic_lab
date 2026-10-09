package si.konferenca.registration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConferenceCatalogueTest {

  @Test
  void ac00109_onlyActiveOptionsOfTheTypeAreSelectable() {
    ConferenceCatalogue catalogue = Fixtures.catalogue();

    assertThat(catalogue.selectableOptions(RegistrationType.STUDENT, Category.WORKSHOP))
        .extracting(ConferenceOption::id)
        .containsExactly("ws-a", "ws-b", "ws-c");
    assertThat(catalogue.selectableOptions(RegistrationType.EXTERNAL, Category.WORKSHOP))
        .extracting(ConferenceOption::id)
        .containsExactly("ws-a", "ws-b", "ws-c", "ws-ext");
    assertThat(catalogue.selectableOptions(RegistrationType.EXTERNAL, Category.OTHER)).isEmpty();
  }

  @Test
  void d17_limitsDefaultToOne() {
    ConferenceCatalogue catalogue = Fixtures.catalogue();

    assertThat(catalogue.maxSelections(Category.WORKSHOP)).isEqualTo(2);
    assertThat(catalogue.maxSelections(Category.MEAL)).isEqualTo(1);
  }

  @Test
  void duplicateOptionIdsAreRefused() {
    ConferenceOption option =
        new ConferenceOption("x", "X", Category.MEAL, true, Set.of(RegistrationType.STUDENT));

    assertThatThrownBy(() -> new ConferenceCatalogue(List.of(option, option), Map.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fieldsOfEachTypeAreFixed() {
    assertThat(Field.of(RegistrationType.EXTERNAL))
        .extracting(Field::apiName)
        .containsExactly("firstName", "lastName", "email", "organization");
    assertThat(Field.of(RegistrationType.STUDENT))
        .extracting(Field::apiName)
        .containsExactly(
            "firstName", "lastName", "email", "studyInstitution", "studyProgramme", "studentId");
  }

  @Test
  void typesParseOnlyTheirOwnSpelling() {
    assertThat(RegistrationType.fromPath("student")).contains(RegistrationType.STUDENT);
    assertThat(RegistrationType.fromPath("STUDENT")).isEmpty();
    assertThat(RegistrationType.fromName("EXTERNAL")).contains(RegistrationType.EXTERNAL);
    assertThat(RegistrationType.fromName("external")).isEmpty();
  }
}
