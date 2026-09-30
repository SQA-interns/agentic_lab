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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Black-box HTTP client for the backend REST API (02_contracts/openapi.json). */
public final class Api {

  public static final JsonMapper JSON = JsonMapper.builder().build();

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final String baseUrl;

  public Api(int port) {
    this.baseUrl = "http://127.0.0.1:" + port;
  }

  /** A response with its status, headers and raw body. */
  public record Response(int status, Map<String, java.util.List<String>> headers, byte[] body) {

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
  }

  public Response get(String path, String... headerPairs) {
    return send(HttpRequest.newBuilder(uri(path)).GET(), headerPairs);
  }

  public Response postJson(String path, String json, String... headerPairs) {
    HttpRequest.Builder b =
        HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
    return send(b, headerPairs);
  }

  /** POST /api/registrations from the given client address (X-Forwarded-For). */
  public Response register(Object payload, String clientIp) {
    return postJson("/api/registrations", toJson(payload), "X-Forwarded-For", clientIp);
  }

  public Response register(Object payload) {
    return register(payload, Clients.next());
  }

  public Response export(String username, String password, String... headerPairs) {
    String[] all = headerPairs;
    if (username != null) {
      String token =
          Base64.getEncoder()
              .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
      all = concat(headerPairs, "Authorization", "Basic " + token);
    }
    return get("/api/organizer/registrations.xlsx", all);
  }

  public static String toJson(Object payload) {
    return payload instanceof String s ? s : JSON.writeValueAsString(payload);
  }

  private Response send(HttpRequest.Builder builder, String... headerPairs) {
    for (int i = 0; i < headerPairs.length; i += 2) {
      builder.header(headerPairs[i], headerPairs[i + 1]);
    }
    builder.timeout(Duration.ofSeconds(60));
    try {
      HttpResponse<byte[]> r = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
      return new Response(r.statusCode(), r.headers().map(), r.body());
    } catch (IOException e) {
      throw new IllegalStateException("HTTP call failed: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private URI uri(String path) {
    return URI.create(baseUrl + path);
  }

  private static String[] concat(String[] a, String... b) {
    String[] r = new String[a.length + b.length];
    System.arraycopy(a, 0, r, 0, a.length);
    System.arraycopy(b, 0, r, a.length, b.length);
    return r;
  }
}
