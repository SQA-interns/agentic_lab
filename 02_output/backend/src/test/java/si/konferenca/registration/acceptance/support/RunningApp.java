package si.konferenca.registration.acceptance.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import si.konferenca.registration.RegistrationApplication;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** One running backend, started through its public entry point with the given configuration. */
public final class RunningApp implements AutoCloseable {

  public static final ObjectMapper JSON = JsonMapper.builder().build();
  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  private final Map<String, String> config;
  private final ConfigurableApplicationContext context;
  private final String baseUrl;

  private RunningApp(Map<String, String> config) {
    this.config = new LinkedHashMap<>(config);
    this.context = SpringApplication.run(RegistrationApplication.class, args(config));
    String port = context.getEnvironment().getProperty("local.server.port");
    this.baseUrl = "http://127.0.0.1:" + port;
  }

  public static RunningApp start(Map<String, String> config) {
    return new RunningApp(config);
  }

  public static RunningApp start() {
    return start(AcceptanceEnvironment.defaultConfiguration());
  }

  /** Starts the application and returns the startup failure, or null if it started. */
  public static Throwable startupFailure(Map<String, String> config) {
    try (RunningApp app = start(config)) {
      return null;
    } catch (Throwable t) {
      return t;
    }
  }

  static String[] args(Map<String, String> config) {
    List<String> a = new ArrayList<>();
    config.forEach((k, v) -> a.add("--" + k + "=" + v));
    return a.toArray(String[]::new);
  }

  /** Configuration with one value replaced, keeping the Spring datasource keys in step. */
  public static Map<String, String> withValue(
      Map<String, String> config, String key, String value) {
    Map<String, String> c = new LinkedHashMap<>(config);
    c.put(key, value);
    return c;
  }

  /** The same configuration (database, JSON directory, mail) for a restart. */
  public Map<String, String> config() {
    return new LinkedHashMap<>(config);
  }

  public String organizerEmails() {
    return config.get("ORGANIZER_EMAILS");
  }

  public Path jsonCopyDir() {
    return Path.of(config.get("JSON_COPY_DIR"));
  }

  public String baseUrl() {
    return baseUrl;
  }

  public Response get(String path) {
    return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build());
  }

  public Response postJson(String path, String body) {
    return send(
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build());
  }

  public Response register(Map<String, Object> registration) {
    return postJson("/api/registrations", JSON.writeValueAsString(registration));
  }

  public Response export(String username, String password) {
    HttpRequest.Builder b =
        HttpRequest.newBuilder(URI.create(baseUrl + "/api/admin/registrations/export")).GET();
    if (username != null) {
      String token =
          Base64.getEncoder()
              .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
      b.header("Authorization", "Basic " + token);
    }
    return send(b.build());
  }

  public Response exportAsOrganizer() {
    return export(
        AcceptanceEnvironment.ORGANIZER_USERNAME, AcceptanceEnvironment.ORGANIZER_PASSWORD);
  }

  private static Response send(HttpRequest request) {
    try {
      HttpResponse<byte[]> r = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
      return new Response(r.statusCode(), r.headers().map(), r.body());
    } catch (Exception e) {
      throw new IllegalStateException("HTTP request failed: " + request.uri(), e);
    }
  }

  /** Rows of the registration table (database contract) whose email matches, case-insensitive. */
  public List<Map<String, Object>> registrationRows(String email) {
    return query(
        "SELECT * FROM registration WHERE lower(email) = lower(?) ORDER BY received_at", email);
  }

  public long registrationCount() {
    return ((Number) query("SELECT count(*) AS n FROM registration").get(0).get("n")).longValue();
  }

  public List<Map<String, Object>> optionRows(Object registrationId) {
    return query(
        "SELECT * FROM registration_option WHERE registration_id = ? ORDER BY option_id",
        registrationId);
  }

  public List<Map<String, Object>> query(String sql, Object... params) {
    try (Connection c =
            DriverManager.getConnection(
                config.get("DB_URL"), config.get("DB_USERNAME"), config.get("POSTGRES_PASSWORD"));
        PreparedStatement ps = c.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        ps.setObject(i + 1, params[i]);
      }
      List<Map<String, Object>> rows = new ArrayList<>();
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          Map<String, Object> row = new LinkedHashMap<>();
          for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            Object value =
                "timestamptz".equals(rs.getMetaData().getColumnTypeName(i))
                    ? rs.getObject(i, java.time.OffsetDateTime.class)
                    : rs.getObject(i);
            row.put(rs.getMetaData().getColumnLabel(i), value);
          }
          rows.add(row);
        }
      }
      return rows;
    } catch (Exception e) {
      throw new IllegalStateException("query failed: " + sql, e);
    }
  }

  @Override
  public void close() {
    context.close();
  }

  /** An HTTP response. */
  public record Response(int status, Map<String, List<String>> headers, byte[] body) {

    public String text() {
      return new String(body, StandardCharsets.UTF_8);
    }

    public JsonNode json() {
      return JSON.readTree(body);
    }

    public String header(String name) {
      return headers.entrySet().stream()
          .filter(e -> e.getKey().equalsIgnoreCase(name))
          .map(e -> String.join(",", e.getValue()))
          .findFirst()
          .orElse(null);
    }

    /** Field error codes of a validation problem, keyed by field. */
    public Map<String, String> fieldErrors() {
      Map<String, String> m = new LinkedHashMap<>();
      JsonNode errors = json().path("errors");
      for (JsonNode e : errors) {
        m.put(e.path("field").asString(), e.path("code").asString());
      }
      return m;
    }
  }
}
