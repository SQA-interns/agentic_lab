package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Calls the REST API of docs/02_contracts/openapi.yaml over real HTTP. */
public final class ApiClient {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final String baseUrl;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public ApiClient(int port) {
    this.baseUrl = "http://127.0.0.1:" + port;
  }

  /** A response with its body as bytes; {@link #json()} parses it. */
  public record Response(int status, HttpHeaders headers, byte[] body) {

    public JsonNode json() {
      return JSON.readTree(body);
    }

    public String text() {
      return new String(body, StandardCharsets.UTF_8);
    }

    public String header(String name) {
      return headers.firstValue(name).orElse(null);
    }
  }

  public Response getRegistrationForm() {
    return send(HttpRequest.newBuilder(uri("/api/registration-form")).GET().build());
  }

  public Response register(JsonNode body) {
    return register(JSON.writeValueAsString(body));
  }

  public Response register(String body) {
    return send(
        HttpRequest.newBuilder(uri("/api/registrations"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build());
  }

  /** The export with organizer credentials, or without any when {@code username} is null. */
  public Response export(String username, String password) {
    HttpRequest.Builder request = HttpRequest.newBuilder(uri("/api/export")).GET();
    if (username != null) {
      String credentials = username + ":" + password;
      request.header(
          "Authorization",
          "Basic "
              + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
    }
    return send(request.build());
  }

  public Response exportAsOrganizer() {
    return export(TestStack.ORGANIZER_USERNAME, TestStack.ORGANIZER_PASSWORD);
  }

  private URI uri(String path) {
    return URI.create(baseUrl + path);
  }

  private Response send(HttpRequest request) {
    try {
      HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
      return new Response(response.statusCode(), response.headers(), response.body());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
