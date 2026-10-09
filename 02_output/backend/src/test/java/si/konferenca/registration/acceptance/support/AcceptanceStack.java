package si.konferenca.registration.acceptance.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Local substitutes from environments.md: PostgreSQL and the Mailpit mail catcher, started once per
 * test JVM. Each backend instance gets its own database so tests do not see each other's data.
 */
public final class AcceptanceStack {

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16.15-alpine")
          .withUsername("registration")
          .withPassword("acceptance-db-password");

  private static final GenericContainer<?> MAILPIT =
      new GenericContainer<>("axllent/mailpit:v1.31.1")
          .withExposedPorts(1025, 8025)
          .waitingFor(Wait.forListeningPorts(1025, 8025));

  private static final AtomicInteger DATABASES = new AtomicInteger();
  private static boolean started;

  private AcceptanceStack() {}

  public static synchronized void start() {
    if (!started) {
      POSTGRES.start();
      MAILPIT.start();
      started = true;
    }
  }

  /** Creates an empty database and returns its JDBC URL. */
  public static String newDatabase() {
    start();
    String name = "acceptance_" + DATABASES.incrementAndGet();
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE " + name);
    } catch (SQLException e) {
      throw new IllegalStateException("cannot create test database", e);
    }
    return "jdbc:postgresql://"
        + POSTGRES.getHost()
        + ":"
        + POSTGRES.getMappedPort(5432)
        + "/"
        + name;
  }

  public static String databaseUser() {
    return POSTGRES.getUsername();
  }

  public static String databasePassword() {
    return POSTGRES.getPassword();
  }

  public static String smtpHost() {
    start();
    return MAILPIT.getHost();
  }

  public static int smtpPort() {
    start();
    return MAILPIT.getMappedPort(1025);
  }

  public static String mailpitApi() {
    start();
    return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025);
  }
}
