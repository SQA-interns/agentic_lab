package si.konferenca.registration.acceptance.support;

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

/** Reads the registration storage contract (`registration-storage.sql`) directly. */
public final class Db {

  private static Connection connect() throws SQLException {
    return DriverManager.getConnection(
        AcceptanceEnvironment.POSTGRES.getJdbcUrl(),
        AcceptanceEnvironment.POSTGRES.getUsername(),
        AcceptanceEnvironment.POSTGRES.getPassword());
  }

  public boolean tableExists(String table) {
    return !query("SELECT 1 FROM information_schema.tables WHERE table_name = ?", table).isEmpty();
  }

  /** Removes every registration; does nothing while the schema does not exist yet. */
  public void clear() {
    unblockInserts();
    if (tableExists("registration")) {
      execute("TRUNCATE registration CASCADE");
    }
  }

  public int countRegistrations() {
    if (!tableExists("registration")) {
      return 0;
    }
    return ((Number) query("SELECT count(*) AS n FROM registration").get(0).get("n")).intValue();
  }

  /** The registration row whose email matches, ignoring letter case; null when there is none. */
  public Map<String, Object> registrationByEmail(String email) {
    if (!tableExists("registration")) {
      return null;
    }
    List<Map<String, Object>> rows =
        query("SELECT * FROM registration WHERE lower(email) = lower(?)", email.trim());
    return rows.isEmpty() ? null : rows.get(0);
  }

  public List<Map<String, Object>> options(Object registrationId) {
    return query(
        "SELECT option_id, display_name, category FROM registration_option"
            + " WHERE registration_id = ? ORDER BY position",
        registrationId);
  }

  public List<Map<String, Object>> consents(Object registrationId) {
    return query(
        "SELECT consent_id, consent_text, given_at FROM registration_consent"
            + " WHERE registration_id = ? ORDER BY consent_id",
        registrationId);
  }

  /** Failure injection: every new registration row violates a constraint until unblocked. */
  public void blockInserts() {
    execute(
        "ALTER TABLE registration ADD CONSTRAINT acceptance_block_inserts CHECK (false) NOT VALID");
  }

  public void unblockInserts() {
    if (tableExists("registration")) {
      execute("ALTER TABLE registration DROP CONSTRAINT IF EXISTS acceptance_block_inserts");
    }
  }

  private static void execute(String sql) {
    try (Connection connection = connect();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private static List<Map<String, Object>> query(String sql, Object... parameters) {
    try (Connection connection = connect();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < parameters.length; i++) {
        statement.setObject(i + 1, parameters[i]);
      }
      try (ResultSet resultSet = statement.executeQuery()) {
        ResultSetMetaData meta = resultSet.getMetaData();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
          Map<String, Object> row = new LinkedHashMap<>();
          for (int column = 1; column <= meta.getColumnCount(); column++) {
            row.put(meta.getColumnLabel(column), resultSet.getObject(column));
          }
          rows.add(row);
        }
        return rows;
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }
}
