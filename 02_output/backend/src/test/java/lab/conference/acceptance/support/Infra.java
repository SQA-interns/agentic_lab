package lab.conference.acceptance.support;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Shared, digest-pinned local substitutes (environments.md "test"): PostgreSQL and Mailpit. */
public final class Infra {

  public static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse(
              "postgres@sha256:95206741a5b214807675e14165369d05b93a9cf692223b616d07cca227e74b0b")
          .asCompatibleSubstituteFor("postgres");
  public static final DockerImageName MAILPIT_IMAGE =
      DockerImageName.parse(
          "axllent/mailpit@sha256:4873e5a441ed368f1e98a832f61257a033859f5e829542cd03fb772d2282ea4e");

  private static PostgreSQLContainer<?> sharedPostgres;
  private static GenericContainer<?> sharedMailpit;

  private Infra() {}

  /** Shared PostgreSQL server; each app instance gets its own fresh database. */
  public static synchronized PostgreSQLContainer<?> postgres() {
    if (sharedPostgres == null) {
      sharedPostgres = newPostgres();
      sharedPostgres.start();
    }
    return sharedPostgres;
  }

  /** Shared Mailpit catcher. */
  public static synchronized GenericContainer<?> mailpit() {
    if (sharedMailpit == null) {
      sharedMailpit = newMailpit();
      sharedMailpit.start();
    }
    return sharedMailpit;
  }

  public static PostgreSQLContainer<?> newPostgres() {
    return new PostgreSQLContainer<>(POSTGRES_IMAGE)
        .withDatabaseName("postgres")
        .withUsername("admin")
        .withPassword("admin-" + UUID.randomUUID());
  }

  /** PostgreSQL bound to a fixed host port so it can be stopped and started again. */
  public static PostgreSQLContainer<?> newPostgresOnFixedPort(int hostPort) {
    PostgreSQLContainer<?> container = newPostgres();
    bindFixedPort(container, 5432, hostPort);
    return container;
  }

  public static GenericContainer<?> newMailpit() {
    return new GenericContainer<>(MAILPIT_IMAGE)
        .withExposedPorts(1025, 8025)
        .waitingFor(
            Wait.forHttp("/livez").forPort(8025).withStartupTimeout(Duration.ofSeconds(60)));
  }

  /** Mailpit whose SMTP port is bound to a fixed host port (used to end an SMTP outage). */
  public static GenericContainer<?> newMailpitWithSmtpOnFixedPort(int hostPort) {
    GenericContainer<?> container = newMailpit();
    bindFixedPort(container, 1025, hostPort);
    return container;
  }

  private static void bindFixedPort(
      GenericContainer<?> container, int containerPort, int hostPort) {
    container.withCreateContainerCmdModifier(
        cmd -> {
          Ports bindings = new Ports();
          bindings.bind(
              ExposedPort.tcp(containerPort),
              new Ports.Binding("127.0.0.1", String.valueOf(hostPort)));
          // D-22: keep random host bindings for the other exposed ports (e.g. Mailpit HTTP API).
          for (ExposedPort p : cmd.getExposedPorts()) {
            if (p.getPort() != containerPort) {
              bindings.bind(p, Ports.Binding.empty());
            }
          }
          cmd.getHostConfig().withPortBindings(bindings);
        });
  }

  public static DatabaseRef newDatabase() {
    return newDatabase(postgres());
  }

  /** Creates an empty database with its own owner in the given server. */
  public static DatabaseRef newDatabase(PostgreSQLContainer<?> server) {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    String name = "conf_" + suffix;
    String user = "app_" + suffix;
    String password = "pw-" + UUID.randomUUID();
    try (Connection c =
            DriverManager.getConnection(
                server.getJdbcUrl(), server.getUsername(), server.getPassword());
        Statement s = c.createStatement()) {
      s.execute("CREATE USER " + user + " PASSWORD '" + password + "'");
      s.execute("CREATE DATABASE " + name + " OWNER " + user);
    } catch (SQLException e) {
      throw new IllegalStateException("cannot create test database", e);
    }
    String url =
        "jdbc:postgresql://" + server.getHost() + ":" + server.getMappedPort(5432) + "/" + name;
    return new DatabaseRef(url, user, password);
  }

  public static int freePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /** JDBC coordinates of a test database. */
  public record DatabaseRef(String url, String username, String password) {}
}
