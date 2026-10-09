package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.Category;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.GivenConsent;
import si.konferenca.registration.domain.OptionCatalogue;
import si.konferenca.registration.domain.ParticipantDetails;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.SelectedOption;

class ExportAndFormServiceTest {

  private static final Instant AT = Instant.parse("2026-10-09T08:15:30.120Z");

  private static TransactionTemplate transactions() {
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    return new TransactionTemplate(manager);
  }

  @Test
  void exportHasTheContractColumnsAndOneRowPerRegistration() {
    Registration external =
        new Registration(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            RegistrationType.EXTERNAL,
            new ParticipantDetails("Ana", "Novak", "ana@example.si", "IJS", null, null, null),
            AT,
            List.of(
                new SelectedOption("ev-1", "Reception", Category.EVENT),
                new SelectedOption("ws-1", "AI", Category.WORKSHOP),
                new SelectedOption("ev-2", "Dinner", Category.EVENT)),
            List.of(new GivenConsent("data", "t", AT), new GivenConsent("photos", "p", AT)));
    Registration student =
        new Registration(
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            RegistrationType.STUDENT,
            new ParticipantDetails("Luka", "Kranjc", "luka@example.si", null, "FRI", "RI", "63"),
            AT,
            List.of(new SelectedOption("o-1", "Fair", Category.OTHER)),
            List.of());
    List<Object> written = new ArrayList<>();
    ExportService service =
        new ExportService(
            new RegistrationRepository() {
              @Override
              public boolean existsByEmailNormalized(String emailNormalized) {
                return false;
              }

              @Override
              public void insert(Registration registration) {}

              @Override
              public List<Registration> findAllOldestFirst() {
                return List.of(external, student);
              }
            },
            (sheet, header, rows) -> {
              written.add(sheet);
              written.add(header);
              written.add(rows);
              return new byte[] {42};
            },
            transactions());

    assertThat(service.exportWorkbook()).containsExactly(42);

    assertThat(written.get(0)).isEqualTo("Registrations");
    assertThat(written.get(1)).isEqualTo(ExportService.HEADER);
    @SuppressWarnings("unchecked")
    List<List<String>> rows = (List<List<String>>) written.get(2);
    assertThat(rows.get(0))
        .containsExactly(
            "00000000-0000-0000-0000-000000000001",
            "2026-10-09T08:15:30.120Z",
            "External participant",
            "Ana",
            "Novak",
            "ana@example.si",
            "IJS",
            "",
            "",
            "",
            "AI",
            "Reception; Dinner",
            "",
            "",
            "data (2026-10-09T08:15:30.120Z); photos (2026-10-09T08:15:30.120Z)");
    assertThat(rows.get(1))
        .containsExactly(
            "00000000-0000-0000-0000-000000000002",
            "2026-10-09T08:15:30.120Z",
            "Student",
            "Luka",
            "Kranjc",
            "luka@example.si",
            "",
            "FRI",
            "RI",
            "63",
            "",
            "",
            "",
            "Fair",
            "");
  }

  @Test
  void formDataListsAllCategoriesInOrderWithActiveOptionsOnly() {
    OptionCatalogue catalogue =
        new OptionCatalogue(
            Map.of(Category.MEAL, 3),
            List.of(
                new ConferenceOption(
                    "m-1", "Lunch", Category.MEAL, true, EnumSet.allOf(RegistrationType.class)),
                new ConferenceOption(
                    "m-2", "Old", Category.MEAL, false, EnumSet.allOf(RegistrationType.class))),
            List.of(new ConsentDefinition("data", "t", true)));

    RegistrationFormService.RegistrationForm test =
        new RegistrationFormService(
                catalogue, "Conf", RegistrationFormService.CaptchaMode.TEST, "ignored-key")
            .form();
    RegistrationFormService.RegistrationForm live =
        new RegistrationFormService(
                catalogue, "Conf", RegistrationFormService.CaptchaMode.RECAPTCHA, "site-key")
            .form();

    assertThat(test.conferenceName()).isEqualTo("Conf");
    assertThat(test.siteKey()).as("no key in test mode").isNull();
    assertThat(live.siteKey()).isEqualTo("site-key");
    assertThat(live.captchaMode()).isEqualTo(RegistrationFormService.CaptchaMode.RECAPTCHA);
    assertThat(test.categories())
        .extracting(RegistrationFormService.CategoryOptions::category)
        .containsExactly(Category.WORKSHOP, Category.EVENT, Category.MEAL, Category.OTHER);
    RegistrationFormService.CategoryOptions meals = test.categories().get(2);
    assertThat(meals.maxSelections()).isEqualTo(3);
    assertThat(meals.options()).extracting(ConferenceOption::id).containsExactly("m-1");
    assertThat(test.categories().get(0).maxSelections()).isEqualTo(1);
    assertThat(test.consents()).extracting(ConsentDefinition::id).containsExactly("data");
  }
}
