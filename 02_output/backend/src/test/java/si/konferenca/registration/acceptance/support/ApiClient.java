package si.konferenca.registration.acceptance.support;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Black-box HTTP client for the REST contract (docs/02_contracts/registration-api.openapi.yaml).
 */
public final class ApiClient {

  public static final JsonMapper JSON = JsonMapper.builder().build();
  public static final String XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final String base;

  public ApiClient(int port) {
    this.base = "http://127.0.0.1:" + port;
  }

  /** One HTTP response with helpers to read its body. */
  public record Response(int status, HttpHeaders headers, byte[] body) {

    public JsonNode json() {
      return JSON.readTree(body);
    }

    public String text() {
      return new String(body, UTF_8);
    }

    public String header(String name) {
      return headers.firstValue(name).orElse("");
    }

    public String contentType() {
      return header("Content-Type");
    }
  }

  public Response get(String path, String... headerPairs) {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path)).GET();
    return send(withHeaders(request, headerPairs));
  }

  public Response postJson(String path, Object body, String... headerPairs) {
    String json = body instanceof String s ? s : JSON.writeValueAsString(body);
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(base + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, UTF_8));
    return send(withHeaders(request, headerPairs));
  }

  public Response formConfig() {
    return get("/api/form-config");
  }

  public Response register(JsonNode registration) {
    return postJson("/api/registrations", registration);
  }

  public Response token(String username, String password) {
    ObjectNode credentials = JSON.createObjectNode();
    credentials.put("username", username);
    credentials.put("password", password);
    return postJson("/api/organizer/token", credentials);
  }

  /** Issues an organizer token and asserts that it was issued. */
  public String organizerToken() {
    Response response =
        token(AcceptanceEnvironment.ORGANIZER_USERNAME, AcceptanceEnvironment.ORGANIZER_PASSWORD);
    assertThat(response.status()).as("organizer token status").isEqualTo(200);
    return response.json().get("token").asString();
  }

  public Response export(String bearerToken) {
    return bearerToken == null
        ? get("/api/organizer/registrations/export")
        : get("/api/organizer/registrations/export", "Authorization", "Bearer " + bearerToken);
  }

  private static HttpRequest.Builder withHeaders(HttpRequest.Builder request, String... pairs) {
    for (int i = 0; i + 1 < pairs.length; i += 2) {
      request.header(pairs[i], pairs[i + 1]);
    }
    return request.timeout(Duration.ofSeconds(60));
  }

  private Response send(HttpRequest.Builder request) {
    try {
      HttpResponse<byte[]> response =
          http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
      return new Response(response.statusCode(), response.headers(), response.body());
    } catch (IOException e) {
      throw new IllegalStateException("request failed: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted", e);
    }
  }
}
