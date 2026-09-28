package org.example.conference.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.example.conference.registration.backup.BackupReconciler;
import org.example.conference.registration.backup.BackupStore;
import org.example.conference.registration.service.RegistrationPersister;
import org.example.conference.support.IntegrationTestBase;
import org.example.conference.support.Payloads;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** P-07 / AC-005-03..05: partial storage failures and crash-leftover reconciliation. */
class StorageFailureIT extends IntegrationTestBase {

  private static final String EXTERNAL = "/api/registrations/external";

  @MockitoSpyBean BackupStore backupStore;
  @MockitoSpyBean RegistrationPersister persister;
  @Autowired BackupReconciler reconciler;

  @AfterEach
  void resetSpies() {
    reset(backupStore, persister);
  }

  private int outboxCount() {
    Integer count = jdbc.queryForObject("SELECT count(*) FROM email_outbox", Integer.class);
    return count == null ? 0 : count;
  }

  private String acceptOne() throws Exception {
    String body =
        postJson(EXTERNAL, Payloads.external())
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return objectMapper.readTree(body).get("registrationId").asText();
  }

  /** AC-005-03: JSON write fails -> 503, nothing in DB, nothing published, retry succeeds. */
  @Test
  void backupWriteFailureReturns503AndStoresNothing() throws Exception {
    doThrow(new IOException("disk full")).when(backupStore).publish(any(UUID.class), anyString());
    Map<String, Object> body = Payloads.external();
    postJson(EXTERNAL, body)
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("STORAGE_UNAVAILABLE"));
    assertThat(registrationCount()).isZero();
    assertThat(publishedFileCount()).isZero();
    assertThat(outboxCount()).isZero();

    reset(backupStore);
    postJson(EXTERNAL, body).andExpect(status().isCreated());
    assertThat(registrationCount()).isEqualTo(1);
    assertThat(publishedFileCount()).isEqualTo(1);
  }

  /** AC-005-04: DB commit fails after publish -> file removed, 503, no outbox rows. */
  @Test
  void databaseFailureAfterPublishRemovesFileAndReturns503() throws Exception {
    doThrow(new DataAccessResourceFailureException("connection lost"))
        .when(persister)
        .persist(any(), any(), anyString(), anyString(), anyString(), any());
    postJson(EXTERNAL, Payloads.external())
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("STORAGE_UNAVAILABLE"));
    assertThat(registrationCount()).isZero();
    assertThat(publishedFileCount()).isZero();
    assertThat(outboxCount()).isZero();
  }

  /** AC-005-05: leftovers from crashes are reconciled. */
  @Test
  void reconciliationRepairsCrashLeftovers() throws Exception {
    String kept = acceptOne();
    String missing = acceptOne();
    String corrupted = acceptOne();
    Path registrations = BACKUP_DIR.resolve("registrations");
    Files.delete(registrations.resolve(missing + ".json"));
    Files.writeString(registrations.resolve(corrupted + ".json"), "{\"tampered\":true}");
    UUID orphan = UUID.randomUUID();
    Files.writeString(registrations.resolve(orphan + ".json"), "{\"orphan\":true}");
    Files.writeString(BACKUP_DIR.resolve("staging").resolve(orphan + ".json.tmp"), "partial");

    BackupReconciler.Report report = reconciler.reconcile(Duration.ZERO);

    assertThat(report).isEqualTo(new BackupReconciler.Report(1, 1, 1, 1));
    assertThat(Files.exists(registrations.resolve(orphan + ".json"))).isFalse();
    assertThat(Files.exists(BACKUP_DIR.resolve("orphaned").resolve(orphan + ".json"))).isTrue();
    assertThat(Files.list(BACKUP_DIR.resolve("staging")).count()).isZero();
    for (String id : new String[] {kept, missing, corrupted}) {
      String raw =
          jdbc.queryForObject(
              "SELECT raw_json FROM registration WHERE id = ?::uuid", String.class, id);
      assertThat(Files.readString(registrations.resolve(id + ".json"))).isEqualTo(raw);
    }
    try (var quarantined = Files.list(BACKUP_DIR.resolve("orphaned"))) {
      assertThat(quarantined.map(p -> p.getFileName().toString()))
          .anyMatch(name -> name.startsWith(corrupted + ".mismatch-"));
    }
    assertThat(reconciler.reconcile(Duration.ZERO))
        .isEqualTo(new BackupReconciler.Report(0, 0, 0, 0));
  }

  /** Periodic run must not touch fresh files (possible in-flight requests). */
  @Test
  void reconciliationRespectsGracePeriod() throws Exception {
    acceptOne();
    UUID fresh = UUID.randomUUID();
    Path file = BACKUP_DIR.resolve("registrations").resolve(fresh + ".json");
    Files.writeString(file, "{}");
    assertThat(reconciler.reconcile(Duration.ofMinutes(10)).orphansQuarantined()).isZero();
    assertThat(Files.exists(file)).isTrue();
  }

  /** Empty database with backup files (DB restore scenario): files are never quarantined. */
  @Test
  void reconciliationDoesNotQuarantineWhenDatabaseIsEmpty() throws Exception {
    Path file = BACKUP_DIR.resolve("registrations").resolve(UUID.randomUUID() + ".json");
    Files.writeString(file, "{}");
    assertThat(reconciler.reconcile(Duration.ZERO).orphansQuarantined()).isZero();
    assertThat(Files.exists(file)).isTrue();
  }
}
