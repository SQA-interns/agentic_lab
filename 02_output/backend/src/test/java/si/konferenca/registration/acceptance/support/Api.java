package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/** HTTP client for the REST contract (api.openapi.yaml). */
public final class Api {

  private static final HttpClient CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  private final String baseUrl;

  Api(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public Response getForm() {
    return send(HttpRequest.newBuilder(uri("/api/form")).GET());
  }

  public Response register(Map<String, Object> registration) {
    return registerRaw(Json.write(registration), "application/json");
  }

  public Response registerRaw(String body, String contentType) {
    return send(
        HttpRequest.newBuilder(uri("/api/registrations"))
            .header("Content-Type", contentType)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
  }

  public Response export(String username, String password) {
    HttpRequest.Builder request = HttpRequest.newBuilder(uri("/api/export")).GET();
    if (username != null) {
      String token =
          Base64.getEncoder()
              .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
      request.header("Authorization", "Basic " + token);
    }
    return send(request);
  }

  public Response exportAsOrganizer() {
    return export(AcceptanceStack.ORGANIZER_USERNAME, AcceptanceStack.ORGANIZER_PASSWORD);
  }

  private URI uri(String path) {
    return URI.create(baseUrl + path);
  }

  private static Response send(HttpRequest.Builder request) {
    try {
      HttpResponse<byte[]> response =
          CLIENT.send(
              request.timeout(Duration.ofSeconds(30)).build(),
              HttpResponse.BodyHandlers.ofByteArray());
      return new Response(response.statusCode(), response.headers(), response.body());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
