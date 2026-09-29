package si.konferenca.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationSnapshot;
import si.konferenca.registration.domain.RegistrationSnapshot.SnapshotOption;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.integration.ExcelExportWriter;
import si.konferenca.registration.integration.JsonBackupStore;
import si.konferenca.registration.persistence.ConferenceOptionRepository;
import si.konferenca.registration.persistence.RegistrationRepository;

class OrganizerServiceTest {

  private final RegistrationRepository registrations = mock(RegistrationRepository.class);
  private final ConferenceOptionRepository options = mock(ConferenceOptionRepository.class);
  private final JsonBackupStore backups = mock(JsonBackupStore.class);
  private final OrganizerService service =
      new OrganizerService(
          registrations,
          options,
          new ExcelExportWriter(),
          backups,
          new TransactionTemplate(mock(PlatformTransactionManager.class)));

  private static RegistrationSnapshot snapshot(UUID id, SnapshotOption... selected) {
    return new RegistrationSnapshot(
        1,
        id,
        Instant.EPOCH,
        RegistrationType.EXTERNAL,
        "A",
        "B",
        "a@x.si",
        "IJS",
        null,
        null,
        null,
        Instant.EPOCH,
        List.of(selected));
  }

  private static JsonBackupStore.Entry entry(RegistrationSnapshot s) {
    return new JsonBackupStore.Entry(Path.of(s.registrationId() + ".json"), s);
  }

  @Test
  void restoreCountsRestoredPresentAndFailedAndRecreatesMissingOptionsAsInactive()
      throws IOException {
    UUID present = UUID.randomUUID();
    UUID missing = UUID.randomUUID();
    UUID broken = UUID.randomUUID();
    SnapshotOption vanished = new SnapshotOption("vanished", OptionCategory.EVENT, "Old event");
    when(backups.readAll())
        .thenReturn(
            List.of(
                entry(snapshot(present)),
                entry(snapshot(missing, vanished)),
                entry(snapshot(broken)),
                new JsonBackupStore.Entry(Path.of("garbage.json"), null)));
    when(registrations.existsById(present)).thenReturn(true);
    when(options.findById("vanished")).thenReturn(Optional.empty());
    when(options.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(registrations.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              Registration r = inv.getArgument(0);
              if (r.getId().equals(broken)) {
                throw new DataIntegrityViolationException("constraint");
              }
              return r;
            });

    OrganizerService.RestoreResult result = service.restoreFromBackups();

    assertThat(result).isEqualTo(new OrganizerService.RestoreResult(1, 1, 2));
    ArgumentCaptor<ConferenceOption> created = ArgumentCaptor.forClass(ConferenceOption.class);
    verify(options).save(created.capture());
    assertThat(created.getValue().isActive()).isFalse();
    assertThat(created.getValue().getName()).isEqualTo("Old event");
  }

  @Test
  void restoreReusesExistingOptions() throws IOException {
    UUID id = UUID.randomUUID();
    ConferenceOption existing = new ConferenceOption("ws", OptionCategory.WORKSHOP, "W", true, 0);
    when(backups.readAll())
        .thenReturn(
            List.of(entry(snapshot(id, new SnapshotOption("ws", OptionCategory.WORKSHOP, "W")))));
    when(options.findById("ws")).thenReturn(Optional.of(existing));

    assertThat(service.restoreFromBackups().restored()).isEqualTo(1);
    verify(options, never()).save(any());
  }

  @Test
  void exportOfNoRegistrationsIsAWorkbook() throws IOException {
    when(registrations.findAllByOrderBySubmittedAtAscIdAsc()).thenReturn(List.of());

    byte[] xlsx = service.exportWorkbook();

    assertThat(xlsx).startsWith((byte) 'P', (byte) 'K');
  }
}
