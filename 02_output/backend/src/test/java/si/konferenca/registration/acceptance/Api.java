package si.konferenca.registration.acceptance;

import java.io.IOException;
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
import tools.jackson.databind.ObjectMapper;

/** HTTP access to a backend instance, as a client of the REST contract. */
final class Api {

  static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private Api() {}

  /** An HTTP answer. */
  record Reply(int status, Map<String, List<String>> headers, byte[] body) {

    String text() {
      return new String(body, StandardCharsets.UTF_8);
    }

    JsonNode json() {
      return JSON.readTree(body);
    }

    String header(String name) {
      return headers.entrySet().stream()
          .filter(entry -> entry.getKey().equalsIgnoreCase(name))
          .flatMap(entry -> entry.getValue().stream())
          .findFirst()
          .orElse("");
    }

    /** The field errors of a validation problem as "field:code". */
    List<String> fieldErrors() {
      List<String> errors = new ArrayList<>();
      JsonNode list = body.length == 0 ? null : json().get("errors");
      if (list != null) {
        for (JsonNode error : list) {
          errors.add(error.path("field").asString() + ":" + error.path("code").asString());
        }
      }
      return errors;
    }

    /** The fields named by the field errors of a validation problem. */
    List<String> errorFields() {
      return fieldErrors().stream().map(error -> error.substring(0, error.indexOf(':'))).toList();
    }
  }

  static Reply get(Stack.App app, String path) {
    return send(HttpRequest.newBuilder(URI.create(app.baseUrl() + path)).GET());
  }

  static Reply getAsOrganizer(Stack.App app, String path, String username, String password) {
    String credentials =
        Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    return send(
        HttpRequest.newBuilder(URI.create(app.baseUrl() + path))
            .header("Authorization", "Basic " + credentials)
            .GET());
  }

  static Reply postJson(Stack.App app, String path, Object body) {
    return send(
        HttpRequest.newBuilder(URI.create(app.baseUrl() + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))));
  }

  static Reply register(Stack.App app, Map<String, Object> registration) {
    return postJson(app, "/api/registrations", registration);
  }

  static Reply getUrl(String url) {
    return send(HttpRequest.newBuilder(URI.create(url)).GET());
  }

  static Reply deleteUrl(String url) {
    return send(HttpRequest.newBuilder(URI.create(url)).DELETE());
  }

  private static Reply send(HttpRequest.Builder request) {
    try {
      HttpResponse<byte[]> response =
          CLIENT.send(
              request.timeout(Duration.ofSeconds(60)).build(),
              HttpResponse.BodyHandlers.ofByteArray());
      return new Reply(response.statusCode(), response.headers().map(), response.body());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
