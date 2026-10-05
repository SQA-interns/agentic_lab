package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HTTP client for the backend's public REST API (docs/02_contracts/api.openapi.yaml). */
public final class Api {

  public static final JsonMapper JSON = JsonMapper.builder().build();

  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final String baseUrl;

  public Api(int port) {
    this.baseUrl = "http://localhost:" + port;
  }

  /** A response: status, content type and raw body. */
  public record Response(int status, String contentType, byte[] body, HttpResponse<byte[]> raw) {

    public String text() {
      return new String(body, StandardCharsets.UTF_8);
    }

    public JsonNode json() {
      return JSON.readTree(body);
    }

    public String header(String name) {
      return raw.headers().firstValue(name).orElse(null);
    }
  }

  public Response getFormConfig() {
    return send(HttpRequest.newBuilder(uri("/api/form-config")).GET());
  }

  public Response register(Object body) {
    return send(
        HttpRequest.newBuilder(uri("/api/registrations"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))));
  }

  public Response export(String username, String password) {
    HttpRequest.Builder request = HttpRequest.newBuilder(uri("/api/registrations/export")).GET();
    if (username != null) {
      String token =
          Base64.getEncoder()
              .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
      request.header("Authorization", "Basic " + token);
    }
    return send(request);
  }

  private URI uri(String path) {
    return URI.create(baseUrl + path);
  }

  private Response send(HttpRequest.Builder request) {
    try {
      HttpResponse<byte[]> response =
          client.send(
              request.timeout(Duration.ofSeconds(30)).build(),
              HttpResponse.BodyHandlers.ofByteArray());
      return new Response(
          response.statusCode(),
          response.headers().firstValue("Content-Type").orElse(""),
          response.body(),
          response);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
