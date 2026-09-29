package si.konferenca.registration.acceptance.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the registration store through its SQL contract (specification section 6). */
public final class Db {

  private final String url;
  private final String user;
  private final String password;

  Db(String url, String user, String password) {
    this.url = url;
    this.user = user;
    this.password = password;
  }

  public int countRegistrations(String email) {
    List<Map<String, Object>> rows =
        query("SELECT count(*) AS n FROM registration WHERE email = ?", email);
    return ((Number) rows.get(0).get("n")).intValue();
  }

  public Map<String, Object> registrationByReference(String reference) {
    List<Map<String, Object>> rows =
        query("SELECT * FROM registration WHERE reference = CAST(? AS uuid)", reference);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public List<String> optionIds(String reference) {
    return query(
            "SELECT o.option_id FROM registration_option o JOIN registration r"
                + " ON r.id = o.registration_id WHERE r.reference = CAST(? AS uuid)"
                + " ORDER BY o.option_id",
            reference)
        .stream()
        .map(r -> (String) r.get("option_id"))
        .toList();
  }

  public List<Map<String, Object>> consents(String reference) {
    return query(
        "SELECT c.consent_id, c.consent_text, c.given_at FROM registration_consent c"
            + " JOIN registration r ON r.id = c.registration_id"
            + " WHERE r.reference = CAST(? AS uuid)",
        reference);
  }

  /** Runs a parameterised query; returns rows keyed by lower-case column label. */
  public List<Map<String, Object>> query(String sql, Object... params) {
    try (Connection c = DriverManager.getConnection(url, user, password);
        PreparedStatement ps = c.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        ps.setObject(i + 1, params[i]);
      }
      try (ResultSet rs = ps.executeQuery()) {
        ResultSetMetaData md = rs.getMetaData();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
          Map<String, Object> row = new LinkedHashMap<>();
          for (int i = 1; i <= md.getColumnCount(); i++) {
            row.put(md.getColumnLabel(i).toLowerCase(java.util.Locale.ROOT), rs.getObject(i));
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
