package lab.conference.acceptance.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Observes the two durable stores named by US-005 through their documented layout: the database
 * tables in docs/02_specification.md section 5 and the JSON store directories in section 4.
 */
public final class Store {

  private final Infra.DatabaseRef db;
  private final Path jsonDir;

  Store(Infra.DatabaseRef db, Path jsonDir) {
    this.db = db;
    this.jsonDir = jsonDir;
  }

  public Path registrationsDir() {
    return jsonDir.resolve("registrations");
  }

  public Path orphanedDir() {
    return jsonDir.resolve("orphaned");
  }

  public Path jsonFile(String registrationId) {
    return registrationsDir().resolve(registrationId + ".json");
  }

  public long registrationCount() {
    return count("SELECT count(*) FROM registration");
  }

  public long outboxCount() {
    return count("SELECT count(*) FROM notification_outbox");
  }

  public long pendingNotifications() {
    return count("SELECT count(*) FROM notification_outbox WHERE status = 'PENDING'");
  }

  public long registrationsWithEmail(String email) {
    return count("SELECT count(*) FROM registration WHERE email = ?", email);
  }

  /** Column values of one registration row, or empty map if absent. */
  public Map<String, Object> registrationRow(String registrationId) {
    try (Connection c = connect();
        PreparedStatement ps =
            c.prepareStatement("SELECT * FROM registration WHERE id = CAST(? AS uuid)")) {
      ps.setString(1, registrationId);
      try (ResultSet rs = ps.executeQuery()) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (rs.next()) {
          for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
          }
        }
        return row;
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  public List<Path> jsonFiles() {
    return list(registrationsDir());
  }

  public List<Path> orphanedFiles() {
    return list(orphanedDir());
  }

  private static List<Path> list(Path dir) {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> s = Files.list(dir)) {
      return s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public Snapshot snapshot() {
    return new Snapshot(registrationCount(), outboxCount(), jsonFiles().size());
  }

  /** Asserts that neither store changed since the snapshot (the "nothing stored" rule). */
  public void assertUnchangedSince(Snapshot before) {
    assertThat(snapshot()).as("database and JSON store unchanged").isEqualTo(before);
  }

  private long count(String sql, Object... params) {
    try (Connection c = connect();
        PreparedStatement ps = c.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        ps.setObject(i + 1, params[i]);
      }
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private Connection connect() throws SQLException {
    return DriverManager.getConnection(db.url(), db.username(), db.password());
  }

  /** Counts of accepted rows, notification intents and JSON files. */
  public record Snapshot(long registrations, long notifications, int jsonFiles) {}
}
