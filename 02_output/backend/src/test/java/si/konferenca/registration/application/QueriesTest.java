package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.OptionsCatalogue;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.Registration.Consent;
import si.konferenca.registration.domain.RegistrationStore;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.TextField;

/** The two read use cases pass on exactly what their ports give them. */
class QueriesTest {

  @Test
  void formQueriesOffersTheActiveOptionsOfTheCatalogue() {
    List<ConferenceOption> active =
        List.of(new ConferenceOption("a", "A", OptionCategory.MEAL, true));
    FormQueries queries =
        new FormQueries(
            new OptionsCatalogue() {
              @Override
              public List<ConferenceOption> activeOptions() {
                return active;
              }

              @Override
              public Optional<ConferenceOption> findActive(String id) {
                return Optional.empty();
              }
            });

    assertThat(queries.activeOptions()).isEqualTo(active);
  }

  @Test
  void exportRendersEveryStoredRegistration() {
    Instant now = Instant.parse("2026-10-02T10:15:30Z");
    Registration registration =
        new Registration(
            UUID.randomUUID(),
            RegistrationType.EXTERNAL,
            now,
            Map.of(TextField.FIRST_NAME, "Ana"),
            List.of(),
            new Consent("personal-data", "Soglašam.", now));
    RegistrationStore store =
        new RegistrationStore() {
          @Override
          public void insert(Registration ignored) {
            throw new UnsupportedOperationException();
          }

          @Override
          public List<Registration> findAll() {
            return List.of(registration, registration);
          }
        };
    ExportRegistrations export =
        new ExportRegistrations(store, registrations -> new byte[] {(byte) registrations.size()});

    assertThat(export.export()).containsExactly(2);
  }
}
