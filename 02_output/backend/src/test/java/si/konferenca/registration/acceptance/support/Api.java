package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import tools.jackson.databind.JsonNode;

/** Black-box HTTP client for the backend's public REST interface (`openapi.yaml`). */
public final class Api {

  private static final HttpClient CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private final URI base;

  public Api(URI base) {
    this.base = base;
  }

  public Response get(String path, String... headers) {
    HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path)).GET();
    if (headers.length > 0) {
      request.headers(headers);
    }
    return send(request);
  }

  public Response postJson(String path, Object body) {
    return postRaw(path, "application/json", Json.write(body));
  }

  public Response postRaw(String path, String contentType, String body) {
    return send(
        HttpRequest.newBuilder(base.resolve(path))
            .header("Content-Type", contentType)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
  }

  public Response formConfig() {
    return get("/api/form-config");
  }

  public Response register(Object body) {
    return postJson("/api/registrations", body);
  }

  public Response export(String username, String password) {
    String token =
        Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    return get("/api/export/registrations.xlsx", "Authorization", "Basic " + token);
  }

  public Response exportWithoutCredentials() {
    return get("/api/export/registrations.xlsx");
  }

  private static Response send(HttpRequest.Builder request) {
    try {
      HttpResponse<byte[]> response =
          CLIENT.send(
              request.timeout(Duration.ofSeconds(60)).build(),
              HttpResponse.BodyHandlers.ofByteArray());
      return new Response(response.statusCode(), response.headers(), response.body());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /** An HTTP response with helpers for the JSON bodies of `openapi.yaml`. */
  public record Response(int status, HttpHeaders headers, byte[] body) {

    public String text() {
      return new String(body, StandardCharsets.UTF_8);
    }

    public JsonNode json() {
      return Json.read(body);
    }

    public String header(String name) {
      return headers.firstValue(name).orElse(null);
    }

    @Override
    public String toString() {
      return "HTTP " + status + " " + (body.length > 2000 ? text().substring(0, 2000) : text());
    }
  }
}
