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

/** Reads the registration storage of docs/02_contracts/db-schema.sql and injects faults. */
public final class Database {

  private Database() {}

  private static Connection connect() throws SQLException {
    return DriverManager.getConnection(
        TestEnvironment.POSTGRES.getJdbcUrl(),
        TestEnvironment.POSTGRES.getUsername(),
        TestEnvironment.POSTGRES.getPassword());
  }

  public static List<Map<String, Object>> rows(String sql, Object... params) {
    try (Connection c = connect();
        PreparedStatement s = c.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        s.setObject(i + 1, params[i]);
      }
      try (ResultSet rs = s.executeQuery()) {
        ResultSetMetaData meta = rs.getMetaData();
        List<Map<String, Object>> result = new ArrayList<>();
        while (rs.next()) {
          Map<String, Object> row = new LinkedHashMap<>();
          for (int i = 1; i <= meta.getColumnCount(); i++) {
            row.put(meta.getColumnName(i), rs.getObject(i));
          }
          result.add(row);
        }
        return result;
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  public static void execute(String sql) {
    try (Connection c = connect();
        Statement s = c.createStatement()) {
      s.execute(sql);
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Number of stored registrations; 0 while the table does not exist yet. */
  public static long registrationCount() {
    if (!tableExists()) {
      return 0;
    }
    return ((Number) rows("SELECT count(*) AS n FROM registration").get(0).get("n")).longValue();
  }

  public static boolean tableExists() {
    Object name = rows("SELECT to_regclass('public.registration') AS t").get(0).get("t");
    return name != null;
  }

  /** Removes every registration; nothing to do before the schema exists. */
  public static void reset() {
    if (tableExists()) {
      execute("TRUNCATE registration CASCADE");
    }
  }

  /**
   * Makes every insert into registration fail when its transaction commits. Without the schema
   * nothing can be stored anyway, so there is nothing to inject.
   */
  public static void failOnCommit() {
    if (!tableExists()) {
      return;
    }
    execute(
        "CREATE OR REPLACE FUNCTION acceptance_fail() RETURNS trigger LANGUAGE plpgsql AS"
            + " $$ BEGIN RAISE EXCEPTION 'injected commit failure'; END $$");
    execute("DROP TRIGGER IF EXISTS acceptance_fail_commit ON registration");
    execute(
        "CREATE CONSTRAINT TRIGGER acceptance_fail_commit AFTER INSERT ON registration"
            + " DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION acceptance_fail()");
  }

  public static void removeFaults() {
    if (tableExists()) {
      execute("DROP TRIGGER IF EXISTS acceptance_fail_commit ON registration");
    }
    execute("DROP FUNCTION IF EXISTS acceptance_fail()");
  }
}
