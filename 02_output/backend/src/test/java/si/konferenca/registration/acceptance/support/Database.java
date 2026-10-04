package si.konferenca.registration.acceptance.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only checks of the storage contract (docs/02_contracts/registration-storage.sql) and failure
 * injection for storage tests.
 */
public final class Database {

  private final String url;
  private final String user;
  private final String password;

  Database(String url, String user, String password) {
    this.url = url;
    this.user = user;
    this.password = password;
  }

  /** Number of registrations whose normalised email equals the trimmed, lower-cased address. */
  public int countByEmail(String email) {
    return queryInt(
        "SELECT count(*) FROM registration WHERE email_normalized = ?",
        email.strip().toLowerCase(java.util.Locale.ROOT));
  }

  public int countAll() {
    return queryInt("SELECT count(*) FROM registration");
  }

  /** The registration row (column name to value) of an address, if stored. */
  public Optional<Map<String, Object>> registrationByEmail(String email) {
    List<Map<String, Object>> rows =
        rows(
            "SELECT * FROM registration WHERE email_normalized = ?",
            email.strip().toLowerCase(java.util.Locale.ROOT));
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  /** Selected options of a registration in selection order, as option_id/name/category rows. */
  public List<Map<String, Object>> options(UUID registrationId) {
    return rows(
        "SELECT option_id, option_name, option_category FROM registration_option"
            + " WHERE registration_id = ? ORDER BY position",
        registrationId);
  }

  /** Asserts that the storage schema exists, i.e. that storage has been built. */
  public void requireStorageSchema() {
    assertThat(queryInt("SELECT count(*) FROM pg_tables WHERE tablename = 'registration'"))
        .as("registration table exists (storage is built)")
        .isEqualTo(1);
  }

  /** Makes every insert into the registration table fail (true) or work again (false). */
  public void failInserts(boolean fail) {
    if (fail) {
      requireStorageSchema();
      execute(
          "CREATE OR REPLACE FUNCTION acceptance_fail_insert() RETURNS trigger AS $$"
              + " BEGIN RAISE EXCEPTION 'injected storage failure'; END $$ LANGUAGE plpgsql");
      execute(
          "CREATE TRIGGER acceptance_fail_insert BEFORE INSERT ON registration"
              + " FOR EACH ROW EXECUTE FUNCTION acceptance_fail_insert()");
    } else {
      execute("DROP TRIGGER IF EXISTS acceptance_fail_insert ON registration");
    }
  }

  /** Creates an empty database in the same server. */
  public void createDatabase(String name) {
    execute("CREATE DATABASE " + name);
  }

  private int queryInt(String sql, Object... params) {
    Object value = rows(sql, params).get(0).values().iterator().next();
    return ((Number) value).intValue();
  }

  private List<Map<String, Object>> rows(String sql, Object... params) {
    try (Connection c = DriverManager.getConnection(url, user, password);
        PreparedStatement s = c.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        s.setObject(i + 1, params[i]);
      }
      try (ResultSet r = s.executeQuery()) {
        ResultSetMetaData meta = r.getMetaData();
        List<Map<String, Object>> result = new ArrayList<>();
        while (r.next()) {
          Map<String, Object> row = new LinkedHashMap<>();
          for (int col = 1; col <= meta.getColumnCount(); col++) {
            row.put(meta.getColumnName(col), r.getObject(col));
          }
          result.add(row);
        }
        return result;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("query failed: " + e.getMessage(), e);
    }
  }

  private void execute(String sql) {
    try (Connection c = DriverManager.getConnection(url, user, password);
        Statement s = c.createStatement()) {
      s.execute(sql);
    } catch (SQLException e) {
      throw new IllegalStateException("statement failed: " + e.getMessage(), e);
    }
  }
}
