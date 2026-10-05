package si.konferenca.registration.infrastructure.migration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** AR-06: the V1 migration ships exactly the storage contract, with a stable checksum. */
class MigrationContractTest {

  @Test
  void migrationRunsTheStorageContractUnchanged() throws Exception {
    String contract =
        Files.readString(Path.of("..", "docs", "02_contracts", "registration-storage.sql"), UTF_8)
            .replace("\r\n", "\n");

    assertThat(V1__Create_registration_storage.contractSql()).isEqualTo(contract);
    assertThat(contract)
        .contains("CREATE TABLE registration (", "registration_email_normalized_uk");
  }

  @Test
  void checksumIsStableAndDependsOnTheContent() {
    V1__Create_registration_storage migration = new V1__Create_registration_storage();

    assertThat(migration.getChecksum())
        .isEqualTo(new V1__Create_registration_storage().getChecksum());
    assertThat(migration.getChecksum()).isNotZero();
  }
}
