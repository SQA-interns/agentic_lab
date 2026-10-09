package si.konferenca.registration.adapter.jsoncopy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import si.konferenca.registration.application.StorageException;
import si.konferenca.registration.domain.Fixtures;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class FileJsonCopyStoreTest {

  @TempDir Path dir;

  @AfterEach
  void clearSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private long files() throws IOException {
    try (Stream<Path> list = Files.list(dir)) {
      return list.count();
    }
  }

  @Test
  void br07_writesTheAcceptedRegistrationAsJson() throws IOException {
    Registration registration = Fixtures.registration(RegistrationType.STUDENT);

    byte[] bytes = new FileJsonCopyStore(dir).write(registration);

    Path file = dir.resolve(registration.id() + ".json");
    assertThat(Files.readAllBytes(file)).isEqualTo(bytes);
    JsonNode json =
        JsonMapper.builder().build().readTree(new String(bytes, StandardCharsets.UTF_8));
    assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
    assertThat(json.get("type").asString()).isEqualTo("STUDENT");
    assertThat(json.get("participant").get("studentId").asString()).isEqualTo("E1");
    assertThat(json.get("participant").has("organization")).isFalse();
    assertThat(json.get("selectedOptions").get(0).get("category").asString()).isEqualTo("WORKSHOP");
    assertThat(json.get("consents").get(0).get("givenAt").asString())
        .isEqualTo("2026-10-09T10:00:00Z");
    assertThat(files()).isEqualTo(1);
  }

  @Test
  void ar05_copyIsRemovedWhenTheTransactionRollsBack() throws IOException {
    TransactionSynchronizationManager.initSynchronization();
    Registration registration = Fixtures.registration(RegistrationType.EXTERNAL);

    new FileJsonCopyStore(dir).write(registration);
    for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
      s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    assertThat(files()).isZero();
  }

  @Test
  void ar05_copyStaysWhenTheTransactionCommits() throws IOException {
    TransactionSynchronizationManager.initSynchronization();

    new FileJsonCopyStore(dir).write(Fixtures.registration(RegistrationType.EXTERNAL));
    for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
      s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
    }

    assertThat(files()).isEqualTo(1);
  }

  @Test
  void ac00503_unwritableDirectoryFailsWithoutLeavingFiles() throws IOException {
    Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-x------"));
    try {
      assertThatThrownBy(
              () ->
                  new FileJsonCopyStore(dir)
                      .write(Fixtures.registration(RegistrationType.EXTERNAL)))
          .isInstanceOf(StorageException.class);
    } finally {
      Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
    }
    assertThat(files()).isZero();
  }
}
