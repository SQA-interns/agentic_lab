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
import java.util.Optional;

/** Reads the tables of docs/02_contracts/database.sql to check what was stored. */
public final class Database {

  private final String url;
  private final String user;
  private final String password;

  Database(String url, String user, String password) {
    this.url = url;
    this.user = user;
    this.password = password;
  }

  /** Empties the registration tables, if the schema exists yet. */
  public void clear() {
    execute(
        "DO $$ BEGIN IF to_regclass('public.registration') IS NOT NULL THEN"
            + " TRUNCATE registration CASCADE; END IF; END $$");
  }

  public void execute(String sql) {
    try (Connection connection = connect();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  public long countRegistrations() {
    return ((Number) query("SELECT count(*) AS n FROM registration").get(0).get("n")).longValue();
  }

  /** The registration row with this email, compared ignoring letter case. */
  public Optional<Map<String, Object>> registrationByEmail(String email) {
    List<Map<String, Object>> rows =
        query("SELECT * FROM registration WHERE lower(email) = lower(?)", email);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public List<Map<String, Object>> options(Object registrationId) {
    return query(
        "SELECT option_id, option_name, category FROM registration_option"
            + " WHERE registration_id = ? ORDER BY option_id",
        registrationId);
  }

  public List<Map<String, Object>> consents(Object registrationId) {
    return query(
        "SELECT consent_id, consent_text, given_at FROM registration_consent"
            + " WHERE registration_id = ? ORDER BY consent_id",
        registrationId);
  }

  /** Rows as column-name maps; column names are lower case. */
  public List<Map<String, Object>> query(String sql, Object... parameters) {
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
            row.put(meta.getColumnLabel(column).toLowerCase(), resultSet.getObject(column));
          }
          rows.add(row);
        }
        return rows;
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private Connection connect() throws SQLException {
    return DriverManager.getConnection(url, user, password);
  }
}
