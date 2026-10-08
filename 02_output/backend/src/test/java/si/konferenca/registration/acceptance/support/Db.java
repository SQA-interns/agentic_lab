package si.konferenca.registration.acceptance.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read access to the storage contract (database.sql); writes only to seed retention cases. */
public final class Db {

  private final String url;
  private final String user;
  private final String password;

  Db(String url, String user, String password) {
    this.url = url;
    this.user = user;
    this.password = password;
  }

  public long countRegistrations() {
    return queryLong("SELECT count(*) FROM registration");
  }

  /** The registration row whose email equals the given value, as column name to value. */
  public Optional<Map<String, Object>> registrationByEmail(String email) {
    List<Map<String, Object>> rows = query("SELECT * FROM registration WHERE email = ?", email);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public Optional<Map<String, Object>> registrationById(UUID id) {
    List<Map<String, Object>> rows = query("SELECT * FROM registration WHERE id = ?", id);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public List<Map<String, Object>> optionsOf(UUID id) {
    return query(
        "SELECT * FROM registration_option WHERE registration_id = ? ORDER BY option_id", id);
  }

  public List<Map<String, Object>> consentsOf(UUID id) {
    return query(
        "SELECT * FROM registration_consent WHERE registration_id = ? ORDER BY consent_id", id);
  }

  /** Inserts an external registration directly, used to seed retention cases (AC-005-05). */
  public void insertExternal(UUID id, String email, Instant receivedAt) {
    execute(
        "INSERT INTO registration (id, type, first_name, last_name, email, email_normalized,"
            + " organization, received_at) VALUES (?, 'EXTERNAL', 'Old', 'Entry', ?, ?, 'Org', ?)",
        id,
        email,
        email.toLowerCase(java.util.Locale.ROOT),
        Timestamp.from(receivedAt));
    execute(
        "INSERT INTO registration_consent (registration_id, consent_id, consent_text, given_at)"
            + " VALUES (?, 'data-processing', 'text', ?)",
        id,
        Timestamp.from(receivedAt));
  }

  private long queryLong(String sql) {
    return ((Number) query(sql).get(0).values().iterator().next()).longValue();
  }

  private List<Map<String, Object>> query(String sql, Object... params) {
    try (Connection c = DriverManager.getConnection(url, user, password);
        PreparedStatement ps = prepare(c, sql, params);
        ResultSet rs = ps.executeQuery()) {
      List<Map<String, Object>> rows = new ArrayList<>();
      int columns = rs.getMetaData().getColumnCount();
      while (rs.next()) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= columns; i++) {
          row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
        }
        rows.add(row);
      }
      return rows;
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private void execute(String sql, Object... params) {
    try (Connection c = DriverManager.getConnection(url, user, password);
        PreparedStatement ps = prepare(c, sql, params)) {
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private static PreparedStatement prepare(Connection c, String sql, Object... params)
      throws SQLException {
    PreparedStatement ps = c.prepareStatement(sql);
    for (int i = 0; i < params.length; i++) {
      ps.setObject(i + 1, params[i]);
    }
    return ps;
  }
}
