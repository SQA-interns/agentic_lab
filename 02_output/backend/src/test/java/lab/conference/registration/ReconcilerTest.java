package lab.conference.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lab.conference.TestCatalogs;
import lab.conference.platform.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReconcilerTest {

  @TempDir Path root;

  private static AppProperties props() {
    return new AppProperties(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        Duration.ofMinutes(5),
        Duration.ofMinutes(2));
  }

  @Test
  void quarantinesOnlyOldFilesWithoutRows() throws Exception {
    JsonStore store = new JsonStore(root);
    RegistrationRepository repo = mock(RegistrationRepository.class);
    UUID orphan = UUID.randomUUID();
    UUID accepted = UUID.randomUUID();
    UUID fresh = UUID.randomUUID();
    for (UUID id : List.of(orphan, accepted, fresh)) {
      store.publish(id, "{}".getBytes());
      store.settled(id);
    }
    FileTime old = FileTime.from(Instant.now().minus(Duration.ofHours(1)));
    Files.setLastModifiedTime(store.file(orphan), old);
    Files.setLastModifiedTime(store.file(accepted), old);
    when(repo.existsById(accepted)).thenReturn(true);
    new Reconciler(store, repo, Clock.systemUTC(), props()).run();
    assertThat(store.file(orphan)).doesNotExist();
    assertThat(root.resolve("orphaned").resolve(orphan + ".json")).exists();
    assertThat(store.file(accepted)).exists();
    assertThat(store.file(fresh)).exists();
  }

  @Test
  void databaseOutageSkipsReconciliation() throws Exception {
    JsonStore store = new JsonStore(root);
    RegistrationRepository repo = mock(RegistrationRepository.class);
    UUID id = UUID.randomUUID();
    store.publish(id, "{}".getBytes());
    store.settled(id);
    Files.setLastModifiedTime(
        store.file(id), FileTime.from(Instant.now().minus(Duration.ofHours(1))));
    when(repo.existsById(any()))
        .thenThrow(new org.springframework.dao.QueryTimeoutException("down"));
    when(repo.findAllInAcceptanceOrder())
        .thenThrow(new org.springframework.dao.QueryTimeoutException("down"));
    Reconciler r = new Reconciler(store, repo, Clock.systemUTC(), props());
    r.atStartup();
    assertThat(store.file(id)).exists();
  }

  @Test
  void verificationToleratesMissingAndAlteredFiles() throws Exception {
    JsonStore store = new JsonStore(root);
    RegistrationRepository repo = mock(RegistrationRepository.class);
    RegistrationValidator v = new RegistrationValidator(TestCatalogs.withConsent(false));
    var body = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
    body.put("clientRequestId", UUID.randomUUID().toString());
    body.put("captchaToken", "t");
    body.put("firstName", "A");
    body.put("lastName", "B");
    body.put("email", "a@example.test");
    body.put("organization", "O");
    ValidatedRegistration reg = v.validate(FormType.EXTERNAL, body);
    UUID ok = UUID.randomUUID();
    UUID altered = UUID.randomUUID();
    UUID missing = UUID.randomUUID();
    store.publish(ok, "{}".getBytes());
    store.publish(altered, "{\"x\":1}".getBytes());
    when(repo.findAllInAcceptanceOrder())
        .thenReturn(
            List.of(
                new RegistrationEntity(
                    ok,
                    Instant.now(),
                    reg,
                    lab.conference.notifications.Hashes.sha256("{}".getBytes())),
                new RegistrationEntity(altered, Instant.now(), reg, "0".repeat(64)),
                new RegistrationEntity(missing, Instant.now(), reg, "0".repeat(64))));
    new Reconciler(store, repo, Clock.systemUTC(), props()).verifyAcceptedFiles();
    assertThat(store.file(ok)).exists();
  }
}
