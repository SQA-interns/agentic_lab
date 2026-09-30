package si.konferenca.registration.acceptance.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the registration storage through its SQL contract (02_contracts/database-schema.sql). */
public final class Database {

  private Database() {}

  public static long countRegistrations() {
    return query("SELECT count(*) AS n FROM registration").get(0).get("n") instanceof Number n
        ? n.longValue()
        : -1;
  }

  public static long countByEmail(String email) {
    Object n =
        query("SELECT count(*) AS n FROM registration WHERE lower(email) = lower(?)", email)
            .get(0)
            .get("n");
    return ((Number) n).longValue();
  }

  public static Map<String, Object> registrationByEmail(String email) {
    List<Map<String, Object>> rows =
        query("SELECT * FROM registration WHERE lower(email) = lower(?)", email);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public static List<Map<String, Object>> options(Object registrationId) {
    return query(
        "SELECT * FROM registration_option WHERE registration_id = ? ORDER BY position",
        registrationId);
  }

  public static List<Map<String, Object>> consents(Object registrationId) {
    return query(
        "SELECT * FROM registration_consent WHERE registration_id = ? ORDER BY consent_id",
        registrationId);
  }

  public static List<Map<String, Object>> query(String sql, Object... args) {
    try (Connection c =
            DriverManager.getConnection(
                TestEnvironment.POSTGRES.getJdbcUrl(),
                TestEnvironment.POSTGRES.getUsername(),
                TestEnvironment.POSTGRES.getPassword());
        PreparedStatement ps = c.prepareStatement(sql)) {
      for (int i = 0; i < args.length; i++) {
        ps.setObject(i + 1, args[i]);
      }
      try (ResultSet rs = ps.executeQuery()) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int cols = rs.getMetaData().getColumnCount();
        while (rs.next()) {
          Map<String, Object> row = new LinkedHashMap<>();
          for (int i = 1; i <= cols; i++) {
            row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
          }
          rows.add(row);
        }
        return rows;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("query failed: " + e.getMessage(), e);
    }
  }
}
