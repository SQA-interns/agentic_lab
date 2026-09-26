package si.konferenca.registration;

import java.time.Instant;
import java.util.List;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.SelectedOption;

/** Shared test data. */
public final class TestFixtures {

  private TestFixtures() {}

  public static AppProperties.Conference conference() {
    return new AppProperties.Conference(
        List.of(
            new AppProperties.OptionEntry("ws-a", "Workshop A", OptionCategory.WORKSHOP, true),
            new AppProperties.OptionEntry("ws-old", "Old workshop", OptionCategory.WORKSHOP, false),
            new AppProperties.OptionEntry("ev-dinner", "Dinner", OptionCategory.EVENT, true),
            new AppProperties.OptionEntry("meal-lunch", "Lunch", OptionCategory.MEAL, true),
            new AppProperties.OptionEntry("other-tour", "City tour", OptionCategory.OTHER, true)),
        List.of(
            new AppProperties.ConsentEntry("privacy", "Privacy consent", true),
            new AppProperties.ConsentEntry("photos", "Photo consent", false)));
  }

  public static AppProperties properties(AppProperties.Conference conference) {
    return new AppProperties(
        conference,
        new AppProperties.Recaptcha(true, "", "", "http://localhost/unused"),
        new AppProperties.Mail("from@example.org", List.of("organizer@example.org")),
        null,
        new AppProperties.Organizer("organizer", "secret"),
        new AppProperties.RateLimit(10, java.time.Duration.ofMinutes(10)),
        new AppProperties.Request(16384),
        null);
  }

  public static Registration externalRegistration() {
    return Registration.builder()
        .type(RegistrationType.EXTERNAL)
        .firstName("Žiga")
        .lastName("Šušteršič")
        .email("ziga@example.si")
        .organization("Univerza v Ljubljani — FRI")
        .createdAt(Instant.parse("2026-03-01T10:15:30Z"))
        .selectedOptions(
            List.of(
                new SelectedOption("ws-a", "Workshop A", OptionCategory.WORKSHOP),
                new SelectedOption("meal-lunch", "Lunch", OptionCategory.MEAL)))
        .consentIds(List.of("privacy"))
        .build();
  }

  public static Registration studentRegistration() {
    return Registration.builder()
        .type(RegistrationType.STUDENT)
        .firstName("Ana")
        .lastName("Čeč")
        .email("ana@student.example.si")
        .studyInstitution("Fakulteta za računalništvo")
        .studyProgramme("Računalništvo in informatika")
        .studentId("63210000")
        .createdAt(Instant.parse("2026-03-02T08:00:00Z"))
        .consentIds(List.of("privacy"))
        .build();
  }
}
