package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import si.konferenca.registration.domain.GivenConsent;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationRepository;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.SelectedOption;

class ExportServiceTest {

  @Test
  @SuppressWarnings("unchecked")
  void writesEveryStoredRegistrationInSubmissionOrder() {
    RegistrationRepository repository = mock(RegistrationRepository.class);
    WorkbookWriter writer = mock(WorkbookWriter.class);
    Instant at = Instant.parse("2026-09-30T10:00:00Z");
    Registration r =
        new Registration(
            UUID.randomUUID(),
            at,
            new Registration.Details(
                RegistrationType.STUDENT, "A", "B", "a@b.si", null, "UL", "RI", "1"),
            List.of(new SelectedOption("ws", "Workshop", "workshop")),
            List.of(new GivenConsent("privacy", at)));
    when(repository.findAllByOrderBySubmittedAtAsc()).thenReturn(List.of(r));
    when(writer.write(Mockito.anyList())).thenReturn(new byte[] {1, 2});

    byte[] result = new ExportService(repository, writer).exportWorkbook();

    assertThat(result).containsExactly(1, 2);
    ArgumentCaptor<List<RegistrationCopy>> rows = ArgumentCaptor.forClass(List.class);
    Mockito.verify(writer).write(rows.capture());
    RegistrationCopy copy = rows.getValue().get(0);
    assertThat(copy.id()).isEqualTo(r.getId());
    assertThat(copy.studyInstitution()).isEqualTo("UL");
    assertThat(copy.organization()).isNull();
    assertThat(copy.options().get(0).name()).isEqualTo("Workshop");
    assertThat(copy.consents().get(0).givenAt()).isEqualTo(at);
  }
}
