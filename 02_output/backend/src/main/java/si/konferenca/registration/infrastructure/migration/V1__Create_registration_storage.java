package si.konferenca.registration.infrastructure.migration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * First migration (AR-06, ES-08): runs the storage contract docs/02_contracts/registration-storage
 * .sql, shipped unchanged in the jar, so the schema and its contract cannot drift apart.
 */
public class V1__Create_registration_storage extends BaseJavaMigration {

  static final String CONTRACT = "/contracts/registration-storage.sql";

  @Override
  public void migrate(Context context) throws SQLException {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(contractSql());
    }
  }

  @Override
  public Integer getChecksum() {
    CRC32 crc = new CRC32();
    crc.update(contractSql().getBytes(StandardCharsets.UTF_8));
    return (int) crc.getValue();
  }

  static String contractSql() {
    try (InputStream in = V1__Create_registration_storage.class.getResourceAsStream(CONTRACT)) {
      if (in == null) {
        throw new IllegalStateException("storage contract missing from the classpath: " + CONTRACT);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
