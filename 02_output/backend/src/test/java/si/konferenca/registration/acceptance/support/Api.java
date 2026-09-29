package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Black-box HTTP client for the backend's public REST API (docs/02_contracts/openapi.yaml). */
public final class Api {

  public static final JsonMapper JSON = JsonMapper.builder().build();

  private final String baseUrl;
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public Api(int port) {
    this.baseUrl = "http://127.0.0.1:" + port;
  }

  /** A response with its status, headers and body. */
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
          .flatMap(e -> e.getValue().stream())
          .findFirst()
          .orElse(null);
    }

    /** The field names of the error body's field errors. */
    public List<String> errorFields() {
      List<String> fields = new ArrayList<>();
      JsonNode errors = json().path("errors");
      errors.forEach(e -> fields.add(e.path("field").asString()));
      return fields;
    }
  }

  public Response get(String path, String... headers) {
    return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET(), headers);
  }

  public Response postJson(String path, Object body, String... headers) {
    return postRaw(path, JSON.writeValueAsBytes(body), headers);
  }

  public Response postRaw(String path, byte[] body, String... headers) {
    HttpRequest.Builder b =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));
    return send(b, headers);
  }

  public Response register(Map<String, Object> registration) {
    return postJson("/api/registrations", registration);
  }

  public static String basicAuth(String user, String password) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  private Response send(HttpRequest.Builder builder, String... headers) {
    for (int i = 0; i + 1 < headers.length; i += 2) {
      builder.header(headers[i], headers[i + 1]);
    }
    builder.timeout(Duration.ofSeconds(30));
    try {
      HttpResponse<byte[]> r =
          client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
      return new Response(r.statusCode(), r.headers().map(), r.body());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
